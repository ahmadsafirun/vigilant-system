/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
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
package org.jkiss.dbeaver.ext.mysql.tools;

import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.jkiss.dbeaver.ext.mysql.ui.internal.MySQLUIMessages;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.DialogUtils;
import org.jkiss.dbeaver.ui.dialogs.tools.AbstractImportExportWizard;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;

import java.io.File;


class MySQLExportWizardPageSettings extends MySQLWizardPageSettings<MySQLExportWizard>
{

    private Text outputFolderText;
    private Text outputFileText;
    private Combo methodCombo;
    private Button noCreateStatementsCheck;
    private Button addDropStatementsCheck;
    private Button disableKeysCheck;
    private Button extendedInsertsCheck;
    private Button dumpEventsCheck;
    private Button commentsCheck;
    private Button removeDefiner;
    private Button binaryInHex;
    private Button noData;

    MySQLExportWizardPageSettings(MySQLExportWizard wizard)
    {
        super(wizard, MySQLUIMessages.tools_db_export_wizard_page_settings_page_name);
        setTitle(MySQLUIMessages.tools_db_export_wizard_page_settings_page_name);
        setDescription((MySQLUIMessages.tools_db_export_wizard_page_settings_page_description));
    }

    @Override
    public boolean isPageComplete()
    {
        return super.isPageComplete() && wizard.getOutputFolder() != null;
    }

    @Override
    public void createControl(Composite parent)
    {
        Composite composite = UIUtils.createPlaceholder(parent, 1);

        SelectionListener changeListener = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateState();
            }
        };

        Group methodGroup = UIUtils.createControlGroup(composite, MySQLUIMessages.tools_db_export_wizard_page_settings_group_exe_method, 1, GridData.FILL_HORIZONTAL, 0);
        methodCombo = new Combo(methodGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
        methodCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        methodCombo.add(MySQLUIMessages.tools_db_export_wizard_page_settings_combo_item_online_backup);
        methodCombo.add(MySQLUIMessages.tools_db_export_wizard_page_settings_combo_item_lock_tables);
        methodCombo.add(MySQLUIMessages.tools_db_export_wizard_page_settings_combo_item_normal);
        methodCombo.select(wizard.method.ordinal());
        methodCombo.addSelectionListener(changeListener);

        Group settingsGroup = UIUtils.createControlGroup(composite, MySQLUIMessages.tools_db_export_wizard_page_settings_group_settings, 3, GridData.FILL_HORIZONTAL, 0);
        noCreateStatementsCheck = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_no_create, wizard.noCreateStatements);
        noCreateStatementsCheck.addSelectionListener(changeListener);
        addDropStatementsCheck = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_add_drop, wizard.addDropStatements);
        addDropStatementsCheck.addSelectionListener(changeListener);
        disableKeysCheck = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_disable_keys, wizard.disableKeys);
        disableKeysCheck.addSelectionListener(changeListener);
        extendedInsertsCheck = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_ext_inserts, wizard.extendedInserts);
        extendedInsertsCheck.addSelectionListener(changeListener);
        dumpEventsCheck = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_dump_events, wizard.dumpEvents);
        dumpEventsCheck.addSelectionListener(changeListener);
        commentsCheck = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_addnl_comments, wizard.comments);
        commentsCheck.addSelectionListener(changeListener);
        removeDefiner = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_remove_definer, wizard.removeDefiner);
        removeDefiner.addSelectionListener(changeListener);
        binaryInHex = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_binary_hex, wizard.binariesInHex);
        binaryInHex.addSelectionListener(changeListener);
        noData = UIUtils.createCheckbox(settingsGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_checkbox_no_data, wizard.noData);
        noData.addSelectionListener(changeListener);

        Group outputGroup = UIUtils.createControlGroup(composite, MySQLUIMessages.tools_db_export_wizard_page_settings_group_output, 2, GridData.FILL_HORIZONTAL, 0);
        outputFolderText = DialogUtils.createOutputFolderChooser(outputGroup, MySQLUIMessages.tools_db_export_wizard_page_settings_label_out_text, e -> updateState());
        outputFileText = UIUtils.createLabelText(outputGroup, "File name pattern", wizard.getOutputFilePattern());
        UIUtils.setContentProposalToolTip(outputFileText, "Output file name pattern",
            AbstractImportExportWizard.VARIABLE_HOST,
            AbstractImportExportWizard.VARIABLE_DATABASE,
            AbstractImportExportWizard.VARIABLE_TABLE,
            AbstractImportExportWizard.VARIABLE_DATE,
            AbstractImportExportWizard.VARIABLE_TIMESTAMP);
        UIUtils.installContentProposal(
            outputFileText,
            new TextContentAdapter(),
            new SimpleContentProposalProvider(new String[] {
                GeneralUtils.variablePattern(AbstractImportExportWizard.VARIABLE_HOST),
                GeneralUtils.variablePattern(AbstractImportExportWizard.VARIABLE_DATABASE),
                GeneralUtils.variablePattern(AbstractImportExportWizard.VARIABLE_TABLE),
                GeneralUtils.variablePattern(AbstractImportExportWizard.VARIABLE_DATE),
                GeneralUtils.variablePattern(AbstractImportExportWizard.VARIABLE_TIMESTAMP),
                }
            ));
        outputFileText.addModifyListener(e -> wizard.setOutputFilePattern(outputFileText.getText()));

        createExtraArgsInput(outputGroup);

        if (wizard.getOutputFolder() != null) {
            outputFolderText.setText(wizard.getOutputFolder().getAbsolutePath());
        }

        createSecurityGroup(composite);

        setControl(composite);
    }

    @Override
    protected void updateState()
    {
        String fileName = outputFolderText.getText();
        wizard.setOutputFolder(CommonUtils.isEmpty(fileName) ? null : new File(fileName));
        wizard.setOutputFilePattern(outputFileText.getText());
        wizard.setExtraCommandArgs(extraCommandArgsText.getText());
        switch (methodCombo.getSelectionIndex()) {
            case 0: wizard.method = MySQLExportWizard.DumpMethod.ONLINE; break;
            case 1: wizard.method = MySQLExportWizard.DumpMethod.LOCK_ALL_TABLES; break;
            default: wizard.method = MySQLExportWizard.DumpMethod.NORMAL; break;
        }
        wizard.noCreateStatements = noCreateStatementsCheck.getSelection();
        wizard.addDropStatements = addDropStatementsCheck.getSelection();
        wizard.disableKeys = disableKeysCheck.getSelection();
        wizard.extendedInserts = extendedInsertsCheck.getSelection();
        wizard.dumpEvents = dumpEventsCheck.getSelection();
        wizard.comments = commentsCheck.getSelection();
        wizard.removeDefiner = removeDefiner.getSelection();
        wizard.binariesInHex = binaryInHex.getSelection();
        wizard.noData = noData.getSelection();

        getContainer().updateButtons();
    }

}
