package ru.krogon500.grouplesync.entity

import io.objectbox.converter.PropertyConverter

class ArrayToObjectBox: PropertyConverter<List<String>, String> {

    override fun convertToEntityProperty(databaseValue: String?): List<String> {
        return if(databaseValue.isNullOrBlank()){
            ArrayList()
        }else{
            databaseValue.split("||")
        }
    }

    override fun convertToDatabaseValue(entityProperty: List<String>?): String {
        if(entityProperty.isNullOrEmpty()) return ""
        val builder = StringBuilder()
        entityProperty.forEachIndexed { index, s ->
            if(index > 0) builder.append("||")
            builder.append(s)
        }
        return builder.toString()
    }
}