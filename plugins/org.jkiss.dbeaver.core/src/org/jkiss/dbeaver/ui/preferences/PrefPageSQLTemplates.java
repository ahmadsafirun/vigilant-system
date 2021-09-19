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
package org.jkiss.dbeaver.ui.preferences;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.texteditor.templates.TemplatePreferencePage;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.ui.editors.sql.templates.SQLTemplateStore;
import org.jkiss.dbeaver.ui.editors.sql.templates.SQLTemplatesRegistry;

/**
 * Templates preference page
 */
public class PrefPageSQLTemplates extends TemplatePreferencePage implements IWorkbenchPropertyPage {

    public static final String PAGE_ID = "org.jkiss.dbeaver.preferences.main.sql.templates";

    public PrefPageSQLTemplates()
    {
        setPreferenceStore(new PreferenceStoreDelegate(DBeaverCore.getGlobalPreferenceStore()));
        setTemplateStore(SQLTemplatesRegistry.getInstance().getTemplateStore());
        setContextTypeRegistry(SQLTemplatesRegistry.getInstance().getTemplateContextRegistry());
    }

    protected String getFormatterPreferenceKey() {
        return SQLTemplateStore.PREF_STORE_KEY;
    }

    @Override
    public IAdaptable getElement()
    {
        return null;
    }

    @Override
    public void setElement(IAdaptable element)
    {
    }
}
