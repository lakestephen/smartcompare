package config;

import java.util.ArrayList;

/**
* Created by IntelliJ IDEA.
* User: Nick Ebbutt
* Date: 29-Apr-2010
* Time: 14:38:13
*/
public final class Migrations {

    private ArrayList<Migration> migrations = new ArrayList<Migration>();

    public ArrayList<Migration> getMigrations() {
        return migrations;
    }

    public void setMigrations(ArrayList<Migration> migrations) {
        this.migrations = migrations;
    }
}
