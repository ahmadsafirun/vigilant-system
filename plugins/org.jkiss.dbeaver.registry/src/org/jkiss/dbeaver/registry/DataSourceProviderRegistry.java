/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.registry;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.app.DBPRegistryListener;
import org.jkiss.dbeaver.model.connection.DBPConnectionType;
import org.jkiss.dbeaver.model.connection.DBPDataSourceProviderDescriptor;
import org.jkiss.dbeaver.model.connection.DBPDataSourceProviderRegistry;
import org.jkiss.dbeaver.model.connection.DBPEditorContribution;
import org.jkiss.dbeaver.registry.driver.DriverDescriptor;
import org.jkiss.dbeaver.registry.driver.DriverDescriptorSerializerLegacy;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.xml.SAXListener;
import org.jkiss.utils.xml.SAXReader;
import org.jkiss.utils.xml.XMLBuilder;
import org.jkiss.utils.xml.XMLException;
import org.xml.sax.Attributes;

import java.io.*;
import java.net.URL;
import java.util.*;

//import org.eclipse.ui.PlatformUI;
//import org.eclipse.ui.activities.IActivityManager;

public class DataSourceProviderRegistry implements DBPDataSourceProviderRegistry
{
    private static final Log log = Log.getLog(DataSourceProviderRegistry.class);

    private static DataSourceProviderRegistry instance = null;

    public synchronized static DataSourceProviderRegistry getInstance()
    {
        if (instance == null) {
            instance = new DataSourceProviderRegistry();
            instance.loadExtensions(Platform.getExtensionRegistry());
        }
        return instance;
    }

    private final List<DataSourceProviderDescriptor> dataSourceProviders = new ArrayList<>();
    private final List<DBPRegistryListener> registryListeners = new ArrayList<>();
    private final Map<String, DBPConnectionType> connectionTypes = new LinkedHashMap<>();
    private final Map<String, ExternalResourceDescriptor> resourceContributions = new HashMap<>();

    private final List<EditorContributionDescriptor> editorContributors = new ArrayList<>();
    private final Map<String, List<EditorContributionDescriptor>> contributionCategoryMap = new HashMap<>();

    private DataSourceProviderRegistry()
    {
    }

    public void loadExtensions(IExtensionRegistry registry)
    {
        // Load datasource providers from external plugins
        {
            IConfigurationElement[] extElements = registry.getConfigurationElementsFor(DataSourceProviderDescriptor.EXTENSION_ID);
            // Sort - parse providers with parent in the end
            Arrays.sort(extElements, (o1, o2) -> {
                String p1 = o1.getAttribute(RegistryConstants.ATTR_PARENT);
                String p2 = o2.getAttribute(RegistryConstants.ATTR_PARENT);
                if (CommonUtils.equalObjects(p1, p2)) return 0;
                if (p1 == null) return -1;
                if (p2 == null) return 1;
                return 0;
            });
            for (IConfigurationElement ext : extElements) {
                switch (ext.getName()) {
                    case RegistryConstants.TAG_DATASOURCE:
                        DataSourceProviderDescriptor provider = new DataSourceProviderDescriptor(this, ext);
                        dataSourceProviders.add(provider);
                        break;
                }
            }

            for (IConfigurationElement ext : extElements) {
                switch (ext.getName()) {
                    case RegistryConstants.TAG_DATASOURCE_PATCH: {
                        String dsId = ext.getAttribute(RegistryConstants.ATTR_ID);
                        DataSourceProviderDescriptor dataSourceProvider = getDataSourceProvider(dsId);
                        if (dataSourceProvider != null) {
                            dataSourceProvider.patchConfigurationFrom(ext);
                        } else {
                            log.warn("Datasource '" + dsId + "' not found for patch");
                        }
                        break;
                    }
                    case RegistryConstants.TAG_EDITOR_CONTRIBUTION: {
                        // Load tree contributions
                        EditorContributionDescriptor descriptor = new EditorContributionDescriptor(ext);
                        editorContributors.add(descriptor);
                        List<EditorContributionDescriptor> list = contributionCategoryMap.computeIfAbsent(
                            descriptor.getCategory(), k -> new ArrayList<>());
                        list.add(descriptor);
                        break;
                    }
                }
            }

            dataSourceProviders.sort((o1, o2) -> {
                if (o1.isDriversManagable() && !o2.isDriversManagable()) {
                    return 1;
                }
                if (o2.isDriversManagable() && !o1.isDriversManagable()) {
                    return -1;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            });
        }

        // Load drivers
        File driversConfig = DBWorkbench.getPlatform().getConfigurationFile(RegistryConstants.DRIVERS_FILE_NAME);
        if (driversConfig.exists()) {
            loadDrivers(driversConfig);
        }

        // Resolve all driver replacements
        {
            List<DriverDescriptor> allDrivers = new ArrayList<>();
            for (DataSourceProviderDescriptor provider : dataSourceProviders) {
                allDrivers.addAll(provider.getDrivers());
            }
            for (DriverDescriptor driver1 : allDrivers) {
                for (DriverDescriptor driver2 : allDrivers) {
                    if (driver1 != driver2 && driver1.replaces(driver2)) {
                        driver2.setReplacedBy(driver1);
                    }
                }
            }
        }

        int driverCount = 0, customDriverCount = 0;
        for (DataSourceProviderDescriptor pd : dataSourceProviders) {
            for (DriverDescriptor dd : pd.getDrivers()) {
                if (!dd.isDisabled() && dd.getReplacedBy() == null) {
                    driverCount++;
                    if (dd.isCustom()) customDriverCount++;
                }
            }
        }
        log.debug("Total database drivers: " + driverCount + " (" + (driverCount - customDriverCount) + ")");

        // Load connection types
        {
            for (DBPConnectionType ct : DBPConnectionType.SYSTEM_TYPES) {
                connectionTypes.put(ct.getId(), ct);
            }
            File ctConfig = DBWorkbench.getPlatform().getConfigurationFile(RegistryConstants.CONNECTION_TYPES_FILE_NAME);
            if (ctConfig.exists()) {
                loadConnectionTypes(ctConfig);
            }
        }

        // Load external resources information
        {
            IConfigurationElement[] extElements = registry.getConfigurationElementsFor(ExternalResourceDescriptor.EXTENSION_ID);
            for (IConfigurationElement ext : extElements) {
                ExternalResourceDescriptor resource = new ExternalResourceDescriptor(ext);
                resourceContributions.put(resource.getName(), resource);
                if (!CommonUtils.isEmpty(resource.getAlias())) {
                    for (String alias : resource.getAlias().split(",")) {
                        resourceContributions.put(alias, resource);
                    }
                }
            }
        }

    }

    public void dispose()
    {
        synchronized (registryListeners) {
            if (!registryListeners.isEmpty()) {
                log.warn("Some datasource registry listeners are still registered: " + registryListeners);
            }
            registryListeners.clear();
        }
        for (DataSourceProviderDescriptor providerDescriptor : this.dataSourceProviders) {
            providerDescriptor.dispose();
        }
        this.dataSourceProviders.clear();
        this.resourceContributions.clear();
    }

    @Override
    @Nullable
    public DataSourceProviderDescriptor getDataSourceProvider(String id)
    {
        for (DataSourceProviderDescriptor provider : dataSourceProviders) {
            if (provider.getId().equals(id)) {
                return provider;
            }
        }
        return null;
    }

    @Override
    public DBPDataSourceProviderDescriptor makeFakeProvider(String providerID) {
        DataSourceProviderDescriptor provider = new DataSourceProviderDescriptor(this, providerID);
        dataSourceProviders.add(provider);
        return provider;
    }

    public List<DataSourceProviderDescriptor> getDataSourceProviders()
    {
        return dataSourceProviders;
    }

    public List<DataSourceProviderDescriptor> getEnabledDataSourceProviders()
    {
        //IActivityManager activityManager = PlatformUI.getWorkbench().getActivitySupport().getActivityManager();
        List<DataSourceProviderDescriptor> enabled = new ArrayList<>(dataSourceProviders);
/*
        enabled.removeIf(p ->
            !activityManager.getIdentifier(p.getFullIdentifier()).isEnabled()
        );
*/
        return enabled;
    }

    @Nullable
    public DriverDescriptor findDriver(@NotNull String driverIdOrName) {
        // Try to find by ID
        for (DataSourceProviderDescriptor pd : dataSourceProviders) {
            DriverDescriptor driver = pd.getDriver(driverIdOrName);
            if (driver != null) {
                return driver;
            }
        }
        // Try to find by name
        for (DataSourceProviderDescriptor pd : dataSourceProviders) {
            for (DriverDescriptor driver : pd.getDrivers()) {
                if (driver.getName().equalsIgnoreCase(driverIdOrName)) {
                    while (driver.getReplacedBy() != null) {
                        driver = driver.getReplacedBy();
                    }
                    return driver;
                }
            }
        }
        return null;
    }

    //////////////////////////////////////////////
    // Editor contributions

    @Override
    public DBPEditorContribution[] getContributedEditors(String category, DBPDataSourceContainer dataSource) {
        List<EditorContributionDescriptor> ec = contributionCategoryMap.get(category);
        if (ec == null) {
            return new DBPEditorContribution[0];
        }
        List<EditorContributionDescriptor> ecCopy = new ArrayList<>();
        for (EditorContributionDescriptor editor : ec) {
            if (editor.supportsDataSource(dataSource)) {
                ecCopy.add(editor);
            }
        }
        return ecCopy.toArray(new DBPEditorContribution[0]);
    }

    //////////////////////////////////////////////
    // Persistence

    private void loadDrivers(File driversConfig)
    {
        if (driversConfig.exists()) {
            try {
                try (InputStream is = new FileInputStream(driversConfig)) {
                    new SAXReader(is).parse(new DriverDescriptorSerializerLegacy.DriversParser());
                } catch (XMLException ex) {
                    log.warn("Drivers config parse error", ex);
                }
            } catch (Exception ex) {
                log.warn("Error loading drivers from " + driversConfig.getPath(), ex);
            }
        }
    }

    public void saveDrivers()
    {
        File driversConfig = DBWorkbench.getPlatform().getConfigurationFile(RegistryConstants.DRIVERS_FILE_NAME);
        try {
            OutputStream os = new FileOutputStream(driversConfig);
            XMLBuilder xml = new XMLBuilder(os, GeneralUtils.UTF8_ENCODING);
            xml.setButify(true);
            xml.startElement(RegistryConstants.TAG_DRIVERS);
            for (DataSourceProviderDescriptor provider : this.dataSourceProviders) {
                if (provider.isTemporary()) {
                    continue;
                }
                xml.startElement(RegistryConstants.TAG_PROVIDER);
                xml.addAttribute(RegistryConstants.ATTR_ID, provider.getId());
                for (DriverDescriptor driver : provider.getDrivers()) {
                    if (driver.isModified()) {
                        driver.serialize(xml, false);
                    }
                }
                xml.endElement();
            }
            xml.endElement();
            xml.flush();
            os.close();
        }
        catch (Exception ex) {
            log.warn("Error saving drivers", ex);
        }
    }

    private void loadConnectionTypes(File configFile)
    {
        try {
            try (InputStream is = new FileInputStream(configFile)) {
                new SAXReader(is).parse(new ConnectionTypeParser());
            } catch (XMLException ex) {
                log.warn("Can't load connection types config from " + configFile.getPath(), ex);
            }
        }
        catch (Exception ex) {
            log.warn("Error parsing connection types", ex);
        }
    }

    public Collection<DBPConnectionType> getConnectionTypes()
    {
        return connectionTypes.values();
    }

    public DBPConnectionType getConnectionType(String id, DBPConnectionType defaultType)
    {
        DBPConnectionType connectionType = connectionTypes.get(id);
        return connectionType == null ? defaultType : connectionType;
    }

    public void addConnectionType(DBPConnectionType connectionType)
    {
        if (this.connectionTypes.containsKey(connectionType.getId())) {
            log.warn("Duplicate connection type id: " + connectionType.getId());
            return;
        }
        this.connectionTypes.put(connectionType.getId(), connectionType);
    }

    public void removeConnectionType(DBPConnectionType connectionType)
    {
        if (!this.connectionTypes.containsKey(connectionType.getId())) {
            log.warn("Connection type doesn't exist: " + connectionType.getId());
            return;
        }
        this.connectionTypes.remove(connectionType.getId());
    }

    @Override
    public void saveConnectionTypes()
    {
        File ctConfig = DBWorkbench.getPlatform().getConfigurationFile(RegistryConstants.CONNECTION_TYPES_FILE_NAME);
        try {
            OutputStream os = new FileOutputStream(ctConfig);
            XMLBuilder xml = new XMLBuilder(os, GeneralUtils.UTF8_ENCODING);
            xml.setButify(true);
            xml.startElement(RegistryConstants.TAG_TYPES);
            for (DBPConnectionType connectionType : connectionTypes.values()) {
                xml.startElement(RegistryConstants.TAG_TYPE);
                xml.addAttribute(RegistryConstants.ATTR_ID, connectionType.getId());
                xml.addAttribute(RegistryConstants.ATTR_NAME, CommonUtils.toString(connectionType.getName()));
                xml.addAttribute(RegistryConstants.ATTR_COLOR, connectionType.getColor());
                xml.addAttribute(RegistryConstants.ATTR_DESCRIPTION, CommonUtils.toString(connectionType.getDescription()));
                xml.addAttribute(RegistryConstants.ATTR_AUTOCOMMIT, connectionType.isAutocommit());
                xml.addAttribute(RegistryConstants.ATTR_CONFIRM_EXECUTE, connectionType.isConfirmExecute());
                xml.addAttribute(RegistryConstants.ATTR_CONFIRM_DATA_CHANGE, connectionType.isConfirmDataChange());
                xml.endElement();
            }
            xml.endElement();
            xml.flush();
            os.close();
        }
        catch (Exception ex) {
            log.warn("Error saving drivers", ex);
        }
    }

    /**
     * Searches for resource within external resources provided by plugins
     * @param resourcePath path
     * @return URL or null if specified resource not found
     */
    @Nullable
    public URL findResourceURL(String resourcePath)
    {
        ExternalResourceDescriptor descriptor = resourceContributions.get(resourcePath);
        return descriptor == null ? null : descriptor.getURL();
    }

    public void addDataSourceRegistryListener(DBPRegistryListener listener)
    {
        synchronized (registryListeners) {
            registryListeners.add(listener);
        }
    }

    public void removeDataSourceRegistryListener(DBPRegistryListener listener)
    {
        synchronized (registryListeners) {
            registryListeners.remove(listener);
        }
    }

    void fireRegistryChange(DataSourceRegistry registry, boolean load)
    {
        synchronized (registryListeners) {
            for (DBPRegistryListener listener : registryListeners) {
                if (load) {
                    listener.handleRegistryLoad(registry);
                } else {
                    listener.handleRegistryUnload(registry);
                }
            }
        }
    }

    class ConnectionTypeParser implements SAXListener
    {
        @Override
        public void saxStartElement(SAXReader reader, String namespaceURI, String localName, Attributes atts)
            throws XMLException
        {
            if (localName.equals(RegistryConstants.TAG_TYPE)) {
                DBPConnectionType connectionType = new DBPConnectionType(
                    atts.getValue(RegistryConstants.ATTR_ID),
                    atts.getValue(RegistryConstants.ATTR_NAME),
                    atts.getValue(RegistryConstants.ATTR_COLOR),
                    atts.getValue(RegistryConstants.ATTR_DESCRIPTION),
                    CommonUtils.getBoolean(atts.getValue(RegistryConstants.ATTR_AUTOCOMMIT)),
                    CommonUtils.getBoolean(atts.getValue(RegistryConstants.ATTR_CONFIRM_EXECUTE)),
                    CommonUtils.getBoolean(atts.getValue(RegistryConstants.ATTR_CONFIRM_DATA_CHANGE)));
                connectionTypes.put(connectionType.getId(), connectionType);
            }
        }

        @Override
        public void saxText(SAXReader reader, String data) {}

        @Override
        public void saxEndElement(SAXReader reader, String namespaceURI, String localName) {}
    }


}
