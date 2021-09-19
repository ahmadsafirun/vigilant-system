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
package org.jkiss.dbeaver.registry.task;

import org.eclipse.core.runtime.IConfigurationElement;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.DBPNamedObjectLocalized;
import org.jkiss.dbeaver.model.impl.AbstractContextDescriptor;
import org.jkiss.dbeaver.model.task.DBTTaskConfigurator;
import org.jkiss.dbeaver.model.task.DBTTaskDescriptor;
import org.jkiss.dbeaver.model.task.DBTTaskTypeDescriptor;
import org.jkiss.dbeaver.registry.RegistryConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * TaskDescriptor
 */
public class TaskTypeDescriptor extends AbstractContextDescriptor implements DBTTaskTypeDescriptor, DBPNamedObjectLocalized {

    private final IConfigurationElement config;
    private final List<TaskDescriptor> tasks = new ArrayList<>();
    private TaskConfiguratorDescriptor configuratorDescriptor;

    TaskTypeDescriptor(IConfigurationElement config) {
        super(config);
        this.config = config;
    }

    void addTask(TaskDescriptor task) {
        this.tasks.add(task);
    }

    @NotNull
    @Override
    public String getId() {
        return config.getAttribute(RegistryConstants.ATTR_ID);
    }

    @NotNull
    @Override
    public String getName() {
        return config.getAttribute(RegistryConstants.ATTR_NAME);
    }

    @Override
    public String getDescription() {
        return config.getAttribute(RegistryConstants.ATTR_DESCRIPTION);
    }

    @Override
    public DBPImage getIcon() {
        return iconToImage(config.getAttribute(RegistryConstants.ATTR_ICON));
    }

    @NotNull
    @Override
    public DBTTaskDescriptor[] getTasks() {
        return tasks.toArray(new DBTTaskDescriptor[0]);
    }

    @Override
    public boolean supportsConfigurator() {
        return configuratorDescriptor != null;
    }

    @NotNull
    @Override
    public DBTTaskConfigurator createConfigurator() throws DBException {
        if (configuratorDescriptor == null) {
            throw new DBException("No configurator for task type " + getId());
        }
        return configuratorDescriptor.createConfigurator();
    }

    void setConfigurator(TaskConfiguratorDescriptor configurator) {
        this.configuratorDescriptor = configurator;
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public String getLocalizedName(String locale) {
        return config.getAttribute(RegistryConstants.ATTR_NAME, locale);
    }

}
