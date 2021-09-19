/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 * Copyright (C) 2017-2018 Alexander Fedorov (alexander.fedorov@jkiss.org)
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

package org.jkiss.dbeaver.debug.ui.details;

import org.eclipse.swt.widgets.Composite;
import org.jkiss.dbeaver.debug.ui.internal.DebugUIMessages;
import org.jkiss.dbeaver.debug.ui.DebugUI;

public class DatabaseStandardBreakpointPane extends DatabaseDebugDetailPane<DatabaseBreakpointEditor> {

    public static final String DETAIL_PANE_STANDARD_BREAKPOINT = DebugUI.BUNDLE_SYMBOLIC_NAME + '.'
            + "DETAIL_PANE_STANDARD_BREAKPOINT"; //$NON-NLS-1$

    public DatabaseStandardBreakpointPane() {
        super(DebugUIMessages.DatabaseStandardBreakpointPane_name,
                DebugUIMessages.DatabaseStandardBreakpointPane_description, DETAIL_PANE_STANDARD_BREAKPOINT);
    }

    @Override
    protected DatabaseBreakpointEditor createEditor(Composite parent) {
        return new DatabaseBreakpointEditor();
    }

}
