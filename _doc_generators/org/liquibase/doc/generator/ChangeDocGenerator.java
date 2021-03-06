package org.liquibase.doc.generator;


import liquibase.change.*;
import liquibase.change.core.LoadDataChange;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.core.HsqlDatabase;
import liquibase.database.core.MSSQLDatabase;
import liquibase.database.core.MySQLDatabase;
import liquibase.database.core.OracleDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.serializer.core.json.JsonChangeLogSerializer;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import liquibase.serializer.core.yaml.YamlChangeLogSerializer;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.SqlStatement;
import liquibase.util.StringUtils;

import java.io.*;
import java.util.*;

public class ChangeDocGenerator {

    public static void main(String[] args) throws IOException {
        Map<String, SortedSet<Class<? extends Change>>> definedChanges = ChangeFactory.getInstance().getRegistry();
        List<Database> databases = DatabaseFactory.getInstance().getImplementedDatabases();
        Collections.sort(databases, new Comparator<Database>() {
            @Override
            public int compare(Database o1, Database o2) {
                return o1.getDatabaseProductName().compareTo(o2.getDatabaseProductName());
            }
        });

        writeChangeNav(definedChanges);
        writeChangePages(definedChanges, databases);
    }

    private static void writeChangeNav(Map<String, SortedSet<Class<? extends Change>>> definedChanges) throws IOException {
        String content = "";
        content = addGeneratedHeader(content);
        content += "{% include subnav_documentation.md %}\n\n<hr>\n<h3 style='color: #747373'>Bundled Changes</h3>\n\n";

        for (String changeName : new TreeSet<String>(definedChanges.keySet())) {
            content += "<li><a href='"+getChangeDocFileName(changeName)+".html'><span>"+changeName.replaceAll("([A-Z])", " $1") + "</span></a></li>\n";
        }
        File file = new File("_includes/subnav_documentation_changes.md");
        new FileOutputStream(file).write(content.getBytes());
    }

    private static void writeChangePages(Map<String, SortedSet<Class<? extends Change>>> definedChanges, List<Database> databases) throws IOException {
        List<Database> exampleDatabases = new ArrayList<Database>(databases);
        exampleDatabases.add(0, new HsqlDatabase());
        exampleDatabases.add(0, new OracleDatabase());
        exampleDatabases.add(0, new MSSQLDatabase());
        exampleDatabases.add(0, new MySQLDatabase());


        for (String changeName : definedChanges.keySet()) {
            System.out.println("--------------------");

            Change exampleChange = ChangeFactory.getInstance().create(changeName);
            ChangeMetaData changeMetaData = ChangeFactory.getInstance().getChangeMetaData(exampleChange);

            for (ChangeParameterMetaData param : changeMetaData.getParameters().values()) {
                if (param.getDataType().matches(".* of .*")) {
                    if (param.getDataType().endsWith(" of columnConfig")) {
                        ColumnConfig columnConfig = new ColumnConfig();
                        columnConfig.setName("address");
                        columnConfig.setType("varchar(255)");
                        List cols = new ArrayList();
                        cols.add(columnConfig);
                        param.setValue(exampleChange, cols);
                    }
                } else {
                    param.setValue(exampleChange, param.getExampleValue());
                }
            }

            String content = "---\n" +
                    "layout: default\n" +
                    "title: Change " + changeMetaData.getName() + "\n" +
                    "---\n\n";

            content = addGeneratedHeader(content);

            content += "  <script>\n" +
                    "  $(function() {\n" +
                    "    $( \"#changelog-tabs\" ).tabs();\n" +
                    "  });\n" +
                    "</script>\n\n";

            ChangeSet exampleChangeSet = new ChangeSet(exampleChange.getSerializedObjectName()+"-example", "liquibase-docs", false, false, null, null, null, null);
            exampleChangeSet.addChange(exampleChange);

            content += "# Change: '" + changeMetaData.getName() + "'\n\n";
            content += changeMetaData.getDescription().replace("<", "&lt;").replace(">", "&gt;").replace("\\", "\\\\").replace("*", "\\*") + "\n\n";

            content += "## Available Attributes ##\n\n";
            content += "<table>\n";
            content += "<tr><th>Name</th><th>Description</th><th>Required&nbsp;For</th><th>Supports</th><th>Since</th></tr>\n";
            List<ChangeParameterMetaData> params = new ArrayList<ChangeParameterMetaData>(changeMetaData.getParameters().values());
            Collections.sort(params, new Comparator<ChangeParameterMetaData>() {
                @Override
                public int compare(ChangeParameterMetaData o1, ChangeParameterMetaData o2) {
                    return o1.getParameterName().compareTo(o2.getParameterName());
                }
            });

            List<ChangeParameterMetaData> nestedParams = new ArrayList<ChangeParameterMetaData>();
            for (ChangeParameterMetaData param : params) {
                if (param.getDataType().matches(".* of .*")) {
                    nestedParams.add(param);
                    continue;
                }

                Set<String> requiredForDatabase = param.getRequiredForDatabase();
                String required = StringUtils.trimToEmpty(StringUtils.join(requiredForDatabase, ", "));

                Set<String> supportsDatabase = param.getSupportedDatabases();
                String supports = StringUtils.trimToEmpty(StringUtils.join(supportsDatabase, ", "));

                content += "<tr><td style='vertical-align: top'>"+param.getParameterName() + "</td><td style='vertical-align: top'>" + param.getDescription() + "</td><td style='vertical-align: top'>" + required+"</td><td style='vertical-align:top'>"+supports+"</td><td style='vertical-align: top'>"+StringUtils.trimToEmpty(param.getSince())+"</td></tr>\n";
            }
            content += "</table>\n\n";


            if (nestedParams.size() > 0) {
                content += "## Nested Properties ##\n\n";
                content += "<table>\n";
                content += "<tr><th>Name</th><th>Description</th><th>Required&nbsp;For</th><th>Supports</th><th>Multiple&nbsp;Allowed</th><th>Since</th></tr>\n";

                for (ChangeParameterMetaData param : nestedParams) {
                    boolean list = param.getDataType().startsWith("list of");

                    Set<String> requiredForDatabase = param.getRequiredForDatabase();
                    String required = StringUtils.trimToEmpty(StringUtils.join(requiredForDatabase, ", "));

                    Set<String> supportsDatabase = param.getSupportedDatabases();
                    String supports = StringUtils.trimToEmpty(StringUtils.join(supportsDatabase, ", "));

                    String description = param.getDescription();
                    if (param.getDataType().endsWith("columnConfig")) {
                        description += "<br><br>See the <a href='../column.html'>column tag</a> documentation for more information";
                    }

                    content += "<tr><td style='vertical-align: top'>"+ param.getParameterName() + "</td><td style='vertical-align: top'>" + description + "</td><td style='vertical-align: top'>" + required+"</td><td style='vertical-align: top'>"+supports+"</td><td style='vertical-align: top'>"+(list?"yes":"no")+"</td><td style='vertical-align: top'>"+StringUtils.trimToEmpty(param.getSince())+"</td></tr>\n";
                }
                content += "</table>\n";
            }

            content += "<div id='changelog-tabs'>\n" +
                    "<ul>\n" +
                    "    <li><a href=\"#tab-xml\">XML Sample</a></li>\n" +
                    "    <li><a href=\"#tab-yaml\">YAML Sample</a></li>\n" +
                    "    <li><a href=\"#tab-json\">JSON Sample</a></li>\n" +
                    "  </ul>\n";
            content += "<div id='tab-xml'>\n";
            content += "{% highlight xml %}\n";
            content += new XMLChangeLogSerializer().serialize(exampleChangeSet, true);
            content += "\n{% endhighlight %}\n";
            content += "</div>\n";

            content += "<div id='tab-yaml'>\n";
            content += "{% highlight yaml %}\n";
            content += new YamlChangeLogSerializer().serialize(exampleChangeSet, true);
            content += "\n{% endhighlight %}\n";
            content += "</div>\n";

            content += "<div id='tab-json'>\n";
            content += "{% highlight json %}\n";
            content += new JsonChangeLogSerializer().serialize(exampleChangeSet, true);
            content += "\n{% endhighlight %}\n";
            content += "</div>\n";

            content += "</div>\n\n\n";

            Database exampleDatabase = null;
            for (Database db : exampleDatabases) {
                if (exampleChange.supports(db)) {
                    exampleDatabase = db;
                    break;
                }
            }

            if (exampleChange instanceof LoadDataChange) {
                exampleChange.setResourceAccessor(new ClassLoaderResourceAccessor(ChangeDocGenerator.class.getClassLoader()));
                ((LoadDataChange) exampleChange).setFile("org/liquibase/doc/generator/example.csv");
            }

            if (!exampleChange.generateStatementsVolatile(exampleDatabase)) {
                if (exampleDatabase != null) {
                    String sql = "";
                    for (SqlStatement statement : exampleChange.generateStatements(exampleDatabase)) {
                        for (Sql out : SqlGeneratorFactory.getInstance().generateSql(statement, exampleDatabase)) {
                            sql += out.toSql()+out.getEndDelimiter()+"\n\n";
                        }
                    }
                    sql = sql.replace(",",",\n");
                    content += "## SQL Generated From Above Sample ("+exampleDatabase.getDatabaseProductName()+")\n\n";
                    content += "{% highlight sql %}\n";
                    content +=  sql;
                    content += "\n{% endhighlight %}\n\n";
                }
            }

            content += "## Database Support\n\n";
            content += "<table style='border:1;'>\n";
            content += "<tr><th>Database</th><th>Notes</th><th>Auto Rollback</th></tr>\n";
            for (Database database : databases) {
                if (database.getShortName().equals("unsupported")) {
                    continue;
                }

                String supported;
                if (exampleChange.supports(database)) {
                    supported = "<b>Supported</b>";
                } else {
                    supported = "Not Supported";
                }
                String notes = StringUtils.trimToNull(changeMetaData.getNotes(database.getShortName()));
                if (notes != null) {
                    supported += ": "+notes;
                }

                String rollback;
                if (exampleChange.supportsRollback(database)) {
                    rollback = "<b>Yes</b>";
                } else {
                    rollback = "No";
                }

                content += "<tr><td>"+database.getDatabaseProductName()+"</td><td>"+ supported +"</td><td>"+rollback+"</td></tr>\n";
            }
            content += "</table>\n";

            System.out.println(content);

            String changename = changeMetaData.getName();
            File file = new File("documentation/changes/" + getChangeDocFileName(changename)+".md");
            new FileOutputStream(file).write(content.getBytes());



        }
    }

    private static String addGeneratedHeader(String content) {
        content += "<!-- ====================================================== -->\n";
        content += "<!-- GENERATED BY ChangeDocGenerator DO NOT MODIFY MANUALLY -->\n";
        content += "<!-- ====================================================== -->\n\n";
        return content;
    }

    private static String getChangeDocFileName(String changename) {
        return changename.replaceAll("([A-Z])", "_$1").toLowerCase();
    }
}
