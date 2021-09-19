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
package org.jkiss.dbeaver.model.task;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.app.DBPProject;

import java.util.Map;

/**
 * Task manager
 */
public interface DBTTaskManager {

    @NotNull
    DBTTaskRegistry getRegistry();

    @NotNull
    DBPProject getProject();

    @NotNull
    DBTTaskConfiguration[] getTaskConfigurations();

    @NotNull
    DBTTaskDescriptor[] getExistingTaskTypes();

    @NotNull
    DBTTaskConfiguration[] getTaskConfigurations(DBTTaskDescriptor task);

    @NotNull
    DBTTaskConfiguration createTaskConfiguration(
        DBTTaskDescriptor task,
        String label,
        String description,
        Map<String, Object> properties) throws DBException;

    void updateTaskConfiguration(DBTTaskConfiguration task);

    void deleteTaskConfiguration(DBTTaskConfiguration task);


}
