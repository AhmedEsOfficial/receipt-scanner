package com.pharadev.receiptscanner.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.pharadev.receiptscanner.data.database.converters.DatabaseConverters
import com.pharadev.receiptscanner.data.database.dao.CategoryDao
import com.pharadev.receiptscanner.data.database.dao.ReceiptDao
import com.pharadev.receiptscanner.data.database.entities.Category
import com.pharadev.receiptscanner.data.database.entities.Receipt
import com.pharadev.receiptscanner.data.database.migrations.DatabaseMigrations

@Database(
    entities = [Receipt::class, Category::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class ReceiptDatabase : RoomDatabase() {
    
    abstract fun receiptDao(): ReceiptDao
    abstract fun categoryDao(): CategoryDao
    
    companion object {
        const val DATABASE_NAME = "receipt_database"
        
        fun buildDatabase(context: Context): ReceiptDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ReceiptDatabase::class.java,
                DATABASE_NAME
            )
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Insert default categories
                    insertDefaultCategories(db)
                }
            })
            // Add migrations for future database version updates
            // Uncomment and add migrations as needed:
            // .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            // For development, you can use fallbackToDestructiveMigration()
            // but remove this for production builds
            .fallbackToDestructiveMigration()
            .build()
        }
        
        private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
            val defaultCategories = listOf(
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (1, 'Food & Dining', '#FF5722', 'restaurant', 1)",
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (2, 'Groceries', '#4CAF50', 'shopping_cart', 1)",
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (3, 'Transportation', '#2196F3', 'directions_car', 1)",
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (4, 'Entertainment', '#9C27B0', 'movie', 1)",
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (5, 'Shopping', '#FF9800', 'shopping_bag', 1)",
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (6, 'Healthcare', '#F44336', 'local_hospital', 1)",
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (7, 'Utilities', '#607D8B', 'build', 1)",
                "INSERT INTO categories (id, name, color, icon, isDefault) VALUES (8, 'Other', '#9E9E9E', 'category', 1)"
            )
            
            defaultCategories.forEach { sql ->
                db.execSQL(sql)
            }
        }
    }
}