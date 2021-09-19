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
package org.jkiss.dbeaver.ui.controls.itemlist;

import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchSite;
import org.jkiss.dbeaver.core.CoreMessages;
import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.navigator.actions.NavigatorHandlerObjectOpen;
import org.jkiss.dbeaver.ui.controls.ObjectViewerRenderer;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;
import org.jkiss.utils.CommonUtils;

/**
 * DatabaseObjectListControl
 */
public abstract class DatabaseObjectListControl<OBJECT_TYPE extends DBPObject> extends ObjectListControl<OBJECT_TYPE> {

    private IWorkbenchSite site;
    protected DatabaseObjectListControl(
        Composite parent,
        int style,
        IWorkbenchSite site,
        IContentProvider contentProvider)
    {
        super(parent, style, contentProvider);
        this.site = site;
        setFitWidth(true);

        createContextMenu();
    }

    @Override
    protected ObjectViewerRenderer createRenderer()
    {
        return new ObjectListRenderer();
    }

    private void createContextMenu()
    {
        NavigatorUtils.createContextMenu(site, getItemsViewer(), new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                IAction copyAction = new Action(CoreMessages.controls_itemlist_action_copy) {
                    @Override
                    public void run()
                    {
                        String text = getRenderer().getSelectedText();
                        if (!CommonUtils.isEmpty(text)) {
                            UIUtils.setClipboardContents(getDisplay(), TextTransfer.getInstance(), text);
                        }
                    }
                };
                copyAction.setEnabled(!getSelectionProvider().getSelection().isEmpty());
                manager.add(copyAction);
                manager.add(new Separator());
                fillCustomActions(manager);
            }
        });
    }

    private class ObjectListRenderer extends ViewerRenderer {
        @Override
        public boolean isHyperlink(Object cellValue)
        {
            return cellValue instanceof DBSObject;
        }

        @Override
        public void navigateHyperlink(Object cellValue)
        {
            if (cellValue instanceof DBSObject) {
                NavigatorHandlerObjectOpen.openEntityEditor((DBSObject) cellValue);
            }
        }
    }

}
