package com.cayma.alertasos.data

import android.content.Context
import androidx.room.*
import androidx.work.*
import com.cayma.alertasos.network.AlertaRequest
import com.cayma.alertasos.network.SosApiService
import java.util.concurrent.TimeUnit

@Entity(tableName = "alertas_pendientes")
data class AlertaLocalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val usuarioId: String,
    val latitud: Double,
    val longitud: Double,
    val tipo: String,
    val descripcion: String,
    val nivelBateria: Int,
    val timestampMs: Long = System.currentTimeMillis(),
    val sincronizado: Boolean = false
)

@Dao
interface AlertaDao {
    @Insert
    suspend fun insertarAlerta(alerta: AlertaLocalEntity)

    @Query("SELECT * FROM alertas_pendientes WHERE sincronizado = 0")
    suspend fun obtenerPendientes(): List<AlertaLocalEntity>

    @Query("UPDATE alertas_pendientes SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarSincronizado(id: Int)

    // Limpieza automatica (TTL) de registros antiguos ya sincronizados (> 7 dias)
    @Query("DELETE FROM alertas_pendientes WHERE sincronizado = 1 AND timestampMs < :limiteTiempo")
    suspend fun limpiarAntiguos(limiteTiempo: Long)
}

@Database(entities = [AlertaLocalEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertaDao(): AlertaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alerta_cayma_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class SyncAlertasWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).alertaDao()
        val api = SosApiService.create()
        val pendientes = dao.obtenerPendientes()

        for (item in pendientes) {
            try {
                api.enviarAlerta(
                    AlertaRequest(
                        usuario_id = item.usuarioId,
                        latitud = item.latitud,
                        longitud = item.longitud,
                        tipo = item.tipo,
                        descripcion = item.descripcion,
                        nivel_bateria = item.nivelBateria,
                        origen_offline_mesh = true
                    )
                )
                dao.marcarSincronizado(item.id)
            } catch (_: Exception) {
                return Result.retry()
            }
        }
        // Limpiar registros de mas de 7 dias
        dao.limpiarAntiguos(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        return Result.success()
    }
}

object OfflineSyncScheduler {
    fun programarSincronizacionAlVolverInternet(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncAlertasWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncAlertasCayma",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}