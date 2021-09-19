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
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.task.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskRegistry implements DBTTaskRegistry
{
    public static final String EXTENSION_ID = "org.jkiss.dbeaver.task"; //$NON-NLS-1$

    private static final Log log = Log.getLog(TaskRegistry.class);

    private static TaskRegistry instance = null;

    public synchronized static TaskRegistry getInstance()
    {
        if (instance == null) {
            instance = new TaskRegistry(Platform.getExtensionRegistry());
        }
        return instance;
    }

    private final Map<String, TaskCategoryDescriptor> taskCategories = new LinkedHashMap<>();
    private final Map<String, TaskTypeDescriptor> taskDescriptors = new LinkedHashMap<>();
    private final List<DBTTaskListener> taskListeners = new ArrayList<>();

    private TaskRegistry(IExtensionRegistry registry)
    {
        // Load data taskDescriptors from external plugins
        {
            IConfigurationElement[] extElements = registry.getConfigurationElementsFor(EXTENSION_ID);
            for (IConfigurationElement ext : extElements) {
                if ("category".equals(ext.getName())) {
                    TaskCategoryDescriptor descriptor = new TaskCategoryDescriptor(this, ext);
                    taskCategories.put(descriptor.getId(), descriptor);
                }
            }
            for (IConfigurationElement ext : extElements) {
                if ("task".equals(ext.getName())) {
                    String typeId = ext.getAttribute("type");
                    TaskCategoryDescriptor taskType = getTaskCategory(typeId);
                    TaskTypeDescriptor taskDescriptor = new TaskTypeDescriptor(taskType, ext);
                    taskDescriptors.put(taskDescriptor.getId(), taskDescriptor);
                } else if ("configurator".equals(ext.getName())) {
                    String typeId = ext.getAttribute("type");
                    TaskCategoryDescriptor taskType = getTaskCategory(typeId);
                    if (taskType == null) {
                        log.debug("");
                    } else {
                        TaskConfiguratorDescriptor configDescriptor = new TaskConfiguratorDescriptor(taskType, ext);
                        taskType.setConfigurator(configDescriptor);
                    }
                }
            }
        }
    }

    @NotNull
    @Override
    public DBTTaskType[] getAllTaskTypes() {
        return taskDescriptors.values().toArray(new DBTTaskType[0]);
    }

    @Nullable
    @Override
    public DBTTaskType getTaskType(String id) {
        return taskDescriptors.get(id);
    }

    @NotNull
    @Override
    public DBTTaskCategory[] getAllCategories() {
        return taskCategories.values().toArray(new DBTTaskCategory[0]);
    }

    @NotNull
    @Override
    public DBTTaskCategory[] getRootCategories() {
        List<DBTTaskCategory> result = new ArrayList<>();
        for (TaskCategoryDescriptor cat : taskCategories.values()) {
            if (cat.getParent() == null) {
                result.add(cat);
            }
        }
        return result.toArray(new DBTTaskCategory[0]);
    }

    @Override
    public void addTaskListener(DBTTaskListener listener) {
        synchronized (taskListeners) {
            taskListeners.add(listener);
        }
    }

    @Override
    public void removeTaskListener(DBTTaskListener listener) {
        synchronized (taskListeners) {
            if (!taskListeners.remove(listener)) {
                log.debug("Task listener " + listener + " not found");
            }
        }
    }

    void notifyTaskListeners(DBTTaskEvent event) {
        DBTTaskListener[] listenersCopy;
        synchronized (taskListeners) {
            listenersCopy = taskListeners.toArray(new DBTTaskListener[0]);
        }
        for (DBTTaskListener listener : listenersCopy) {
            listener.handleTaskEvent(event);
        }
    }

    @Nullable
    TaskCategoryDescriptor getTaskCategory(String id) {
        return taskCategories.get(id);
    }

}
