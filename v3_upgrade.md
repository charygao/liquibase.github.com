---
layout: default
title: Liquibase version 3.0 Upgrade Guide
---

# 2.x to 3.0 Upgrade Guide #

For the normal Liquibase end user, Liquibase 3.0.0 is a drop-in replacement for any Liquibase 2.0.x version.

For developers of Liquibase extensions, there has been some Java API changes that may impact your code. This page continue to be updated with those changes.

## runOnChange and Other Checksum Related Functionality##

There was a change in how checksums are computed between 2.x and 3.x. We try to keep these changes to a minimum, but unfortunately they do happen, especially when there
are extensive code changes like there was in 3.0.0. Liquibase will detect that there has been a checksum version change and automatically update the checksums accordingly when you
first run 3.0, but that also means Liquibase cannot detect differences between a changeset between a 2.x and a 3.x run.

When Liquibase cannot determine if a changeSet has changed or not it assumes it has not.

Therefore, if you use runOnChange functionality that you think may be triggered, you will want to run Liquibase 3.0 against your target database with a known unchanged changelog file before running a new changelog file.

## API changes ##

**Database.escapeDatabaseObject**

`Database.escapeDatabaseObject` -> `Database.escapeObjectName(String objectName, Class<? extends DatabaseObject> objectType)`

To better support escaping objects, we removed the generic escapeDatabaseObject method in favor of a new one that better describes what you are doing (escaping the name, not escaping the object) and specifies the type of object that is being escaped.

**Database.escape * Name(schema, tableName)**

In 3.0, we now support "catalogs" in addition to "schemas" and so all the escapeTYPEName methods such as escapeTableName, escapeColumnName etc. take an additional catalog parameter now.

**AbstractChange constructor**

In 2.x, change metadata such as priority, name, and description was passed in the constructor. To better support subclassing default Changes, the metadata was moved to a new @DatabaseChange class level annotation.

For example, in 2.x you would create a class as:

    public AddCheckChange() {
        super("addCheck", "Add Check", ChangeMetaData.PRIORITY_DEFAULT);
    }


but in 3.x you would have a no-arg constructor and instead add

    @DatabaseChange(
        name="addCheck",
        description = "Add Check",
        priority = ChangeMetaData.PRIORITY_DEFAULT)

to the class definition.

