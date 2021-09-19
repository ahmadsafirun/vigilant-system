package org.jkiss.dbeaver.ext.greenplum.model;

import org.jkiss.dbeaver.ext.postgresql.model.PostgreTableBase;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreTableRegular;

import static java.lang.String.format;

public class GreenplumWithClauseBuilder {

    public static String generateWithClause(PostgreTableRegular table, PostgreTableBase tableBase) {
        StringBuilder withClauseBuilder = new StringBuilder();

        if (tableSupportsAndHasOids(table) && tableIsGreenPlumWithRelOptions(table, tableBase)) {
            withClauseBuilder.append("\nWITH (\n\tOIDS=").append(table.isHasOids() ? "TRUE" : "FALSE");
            withClauseBuilder.append(format(", %s\n)", String.join(", ", tableBase.getRelOptions())));
        } else if (tableSupportsAndHasOids(table)) {
            withClauseBuilder.append("\nWITH (\n\tOIDS=").append(table.isHasOids() ? "TRUE" : "FALSE");
            withClauseBuilder.append("\n)");
        } else if (tableIsGreenPlumWithRelOptions(table, tableBase)) {
            withClauseBuilder.append(format("\nWITH (\n\t%s\n)", String.join(", ", tableBase.getRelOptions())));
        }

        return withClauseBuilder.toString();
    }

    private static boolean tableSupportsAndHasOids(PostgreTableRegular table) {
        return table.getDataSource().getServerType().supportsOids() && table.isHasOids();
    }

    private static boolean tableIsGreenPlumWithRelOptions(PostgreTableRegular table, PostgreTableBase tableBase) {
        return table instanceof GreenplumTable && tableBase.getRelOptions() != null;
    }
}
