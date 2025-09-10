package com.pharadev.receiptscanner.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for the Receipt Scanner application.
 * Each migration represents a version upgrade of the database schema.
 * 
 * Migration strategy:
 * - Version 1: Initial schema with Receipt and Category entities
 * - Future versions will be added here as needed
 * 
 * Best practices:
 * - Always test migrations thoroughly
 * - Provide backward compatibility when possible
 * - Include data preservation steps when modifying existing tables
 * - Document the changes made in each migration
 */
object DatabaseMigrations {
    
    /**
     * Future migration from version 1 to 2
     * This is a placeholder for when we need to update the database schema
     * 
     * Example migration pattern:
     * val MIGRATION_1_2 = object : Migration(1, 2) {
     *     override fun migrate(database: SupportSQLiteDatabase) {
     *         // Add new columns, tables, or modify existing schema
     *         database.execSQL("ALTER TABLE receipts ADD COLUMN ocrText TEXT")
     *     }
     * }
     */
    
    /**
     * Example: Migration to add OCR text field to receipts
     * This would be used if we decide to store the raw OCR text for future processing
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add OCR text column to receipts table
            database.execSQL("ALTER TABLE receipts ADD COLUMN ocrText TEXT DEFAULT NULL")
            
            // Add index for better search performance
            database.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_ocrText ON receipts(ocrText)")
        }
    }
    
    /**
     * Example: Migration to add expense limits table
     * This would support the premium feature of setting monthly spending limits per category
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create expense_limits table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS expense_limits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    categoryId INTEGER NOT NULL,
                    monthlyLimit TEXT NOT NULL,
                    currentSpent TEXT NOT NULL DEFAULT '0',
                    createdAt TEXT NOT NULL,
                    FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
                )
            """)
            
            // Create index for performance
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expense_limits_categoryId ON expense_limits(categoryId)")
        }
    }
    
    /**
     * Example: Migration to add user preferences table
     * This would store app settings and user preferences
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create user_preferences table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS user_preferences (
                    id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                    currency TEXT NOT NULL DEFAULT 'USD',
                    themeMode TEXT NOT NULL DEFAULT 'SYSTEM',
                    autoBackup INTEGER NOT NULL DEFAULT 0,
                    ocrLanguage TEXT NOT NULL DEFAULT 'en',
                    exportFormat TEXT NOT NULL DEFAULT 'CSV',
                    createdAt TEXT NOT NULL,
                    updatedAt TEXT NOT NULL
                )
            """)
            
            // Insert default preferences
            database.execSQL("""
                INSERT INTO user_preferences (currency, themeMode, autoBackup, ocrLanguage, exportFormat, createdAt, updatedAt)
                VALUES ('USD', 'SYSTEM', 0, 'en', 'CSV', datetime('now'), datetime('now'))
            """)
        }
    }
    
    /**
     * Get all available migrations
     * This array should be updated whenever new migrations are added
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4
    )
    
    /**
     * Destructive migration callback for development
     * Use with caution - this will delete all data
     */
    fun createDestructiveMigrationCallback() = object : androidx.room.RoomDatabase.Callback() {
        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
            super.onDestructiveMigration(db)
            // Log or handle destructive migration
            // This is called when Room cannot find a migration path
        }
    }
}