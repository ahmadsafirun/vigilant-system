/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2017 Serge Rider (serge@jkiss.org)
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
package org.jkiss.dbeaver.model;

import org.jkiss.dbeaver.model.net.DBWHandlerConfiguration;
import org.jkiss.dbeaver.model.net.DBWHandlerDescriptor;

import java.util.Map;

/**
 * Data source configurator.
 * Data source provider may implement this interface to allow extra connection configuration (e.g. SSL).
 */
public interface DBPDataSourceConfigurator
{
    boolean supportsExtraConnectionProperties(DBWHandlerDescriptor handler);

    Map<Object, Object> getExtraConnectionProperties(DBWHandlerConfiguration handlerConfiguration);
}
