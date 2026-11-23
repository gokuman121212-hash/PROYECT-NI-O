package com.cayma.alertasos.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "alertas_offline")
data class AlertaLocalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val usuarioId: String,
    val nombreUsuario: String = "Vecino Arequipa",
    val distrito: String = "Arequipa",
    val sectorReferencia: String = "",
    val latitud: Double,
    val longitud: Double,
    val tipo: String,
    val descripcion: String,
    val nivelBateria: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sincronizado: Boolean = false
)

@Entity(tableName = "mensajes_chat_local")
data class MensajeChatLocalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alertaId: String,
    val remitenteId: String,
    val nombreRemitente: String,
    val rol: String,
    val mensaje: String,
    val origenMesh: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AlertaDao {
    @Insert
    suspend fun insertarAlerta(alerta: AlertaLocalEntity)

    @Query("SELECT * FROM alertas_offline WHERE sincronizado = 0")
    suspend fun obtenerPendientes(): List<AlertaLocalEntity>

    @Query("UPDATE alertas_offline SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarSincronizada(id: Int)

    @Insert
    suspend fun insertarMensajeChat(msg: MensajeChatLocalEntity)

    @Query("SELECT * FROM mensajes_chat_local WHERE alertaId = :alertaId ORDER BY timestamp ASC")
    suspend fun obtenerChatPorAlerta(alertaId: String): List<MensajeChatLocalEntity>
}

@Database(entities = [AlertaLocalEntity::class, MensajeChatLocalEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertaDao(): AlertaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alerta_arequipa_offline_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

data class PerfilCiudadano(
    val usuarioId: String,
    val nombreCompleto: String,
    val telefono: String,
    val distrito: String,
    val sectorReferencia: String,
    val latitud: Double,
    val longitud: Double
)

object SessionManager {
    private const val PREFS = "alerta_arequipa_session"

    fun guardarSesion(context: Context, perfil: PerfilCiudadano) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("usuario_id", perfil.usuarioId)
            putString("nombre", perfil.nombreCompleto)
            putString("telefono", perfil.telefono)
            putString("distrito", perfil.distrito)
            putString("sector", perfil.sectorReferencia)
            putFloat("lat", perfil.latitud.toFloat())
            putFloat("lng", perfil.longitud.toFloat())
            apply()
        }
    }

    fun obtenerSesion(context: Context): PerfilCiudadano? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = sp.getString("usuario_id", null) ?: return null
        return PerfilCiudadano(
            usuarioId = id,
            nombreCompleto = sp.getString("nombre", "Vecino Arequipa") ?: "Vecino Arequipa",
            telefono = sp.getString("telefono", "") ?: "",
            distrito = sp.getString("distrito", "Cercado de Arequipa") ?: "Cercado de Arequipa",
            sectorReferencia = sp.getString("sector", "") ?: "",
            latitud = sp.getFloat("lat", -16.3989f).toDouble(),
            longitud = sp.getFloat("lng", -71.5350f).toDouble()
        )
    }

    fun cerrarSesion(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
