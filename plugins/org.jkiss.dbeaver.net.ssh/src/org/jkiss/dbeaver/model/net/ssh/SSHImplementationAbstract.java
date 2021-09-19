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
package org.jkiss.dbeaver.model.net.ssh;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.app.DBPPlatform;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.net.DBWHandlerConfiguration;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.utils.CommonUtils;

import java.io.File;
import java.io.IOException;

/**
 * SSH tunnel
 */
public abstract class SSHImplementationAbstract implements SSHImplementation {

    private static final Log log = Log.getLog(SSHImplementationAbstract.class);

    // Saved config - used for tunnel invalidate
    private transient int savedLocalPort = 0;
    protected transient DBWHandlerConfiguration savedConfiguration;
    protected transient DBPConnectionConfiguration savedConnectionInfo;

    @Override
    public DBPConnectionConfiguration initTunnel(DBRProgressMonitor monitor, DBPPlatform platform, DBWHandlerConfiguration configuration, DBPConnectionConfiguration connectionInfo)
        throws DBException, IOException
    {
        String dbPortString = connectionInfo.getHostPort();
        if (CommonUtils.isEmpty(dbPortString)) {
            dbPortString = configuration.getDriver().getDefaultPort();
            if (CommonUtils.isEmpty(dbPortString)) {
                throw new DBException("Database port not specified and no default port number for driver '" + configuration.getDriver().getName() + "'");
            }
        }
        String dbHost = connectionInfo.getHostName();

        String sshAuthType = configuration.getStringProperty(SSHConstants.PROP_AUTH_TYPE);
        String sshHost = configuration.getStringProperty(SSHConstants.PROP_HOST);
        int sshPortNum = configuration.getIntProperty(SSHConstants.PROP_PORT);
        int sshLocalPort = configuration.getIntProperty(SSHConstants.PROP_LOCAL_PORT);
        int aliveInterval = configuration.getIntProperty(SSHConstants.PROP_ALIVE_INTERVAL);
        int connectTimeout = configuration.getIntProperty(SSHConstants.PROP_CONNECT_TIMEOUT);
        //String aliveCount = properties.get(SSHConstants.PROP_ALIVE_COUNT);
        if (CommonUtils.isEmpty(sshHost)) {
            throw new DBException("SSH host not specified");
        }
        if (sshPortNum == 0) {
            throw new DBException("SSH port not specified");
        }
        if (CommonUtils.isEmpty(configuration.getUserName())) {
            throw new DBException("SSH user not specified");
        }
        SSHConstants.AuthType authType = SSHConstants.AuthType.PASSWORD;
        if (sshAuthType != null) {
            authType = SSHConstants.AuthType.valueOf(sshAuthType); 
        }
        File privKeyFile = null;
        String privKeyPath = configuration.getStringProperty(SSHConstants.PROP_KEY_PATH);
        if (authType == SSHConstants.AuthType.PUBLIC_KEY) {
            if (CommonUtils.isEmpty(privKeyPath)) {
                throw new DBException("Private key path is empty");
            }
            privKeyFile = new File(privKeyPath);
            if (!privKeyFile.exists()) {
                throw new DBException("Private key file '" + privKeyFile.getAbsolutePath() + "' doesn't exist");
            }
        }
        if (connectTimeout == 0){
            connectTimeout = SSHConstants.DEFAULT_CONNECT_TIMEOUT;
        }

        monitor.subTask("Initiating tunnel at '" + sshHost + "'");
        int dbPort;
        try {
            dbPort = Integer.parseInt(dbPortString);
        } catch (NumberFormatException e) {
            throw new DBException("Bad database port number: " + dbPortString);
        }
        int localPort = savedLocalPort;
        if (localPort == 0 && platform != null) {
            localPort = SSHUtils.findFreePort(platform);
        }
        if (sshLocalPort != 0) {
            if (sshLocalPort > 0) {
                localPort = sshLocalPort;
            }
        }

        setupTunnel(monitor, configuration, dbHost, sshHost, aliveInterval, sshPortNum, privKeyFile, connectTimeout, dbPort, localPort);
        savedLocalPort = localPort;
        savedConfiguration = configuration;
        savedConnectionInfo = connectionInfo;

        connectionInfo = new DBPConnectionConfiguration(connectionInfo);
        String newPortValue = String.valueOf(localPort);
        // Replace database host/port and URL - let's use localhost
        connectionInfo.setHostName(SSHConstants.LOCALHOST_NAME);
        connectionInfo.setHostPort(newPortValue);
        if (configuration.getDriver() != null) {
            // Driver can be null in case of orphan tunnel config (e.g. in network profile)
            String newURL = configuration.getDriver().getDataSourceProvider().getConnectionURL(
                configuration.getDriver(),
                connectionInfo);
            connectionInfo.setUrl(newURL);
        }
        return connectionInfo;
    }

    protected abstract void setupTunnel(
        DBRProgressMonitor monitor,
        DBWHandlerConfiguration configuration,
        String dbHost,
        String sshHost,
        int aliveInterval,
        int sshPortNum,
        File privKeyFile,
        int connectTimeout,
        int dbPort,
        int localPort) throws DBException, IOException;

}
