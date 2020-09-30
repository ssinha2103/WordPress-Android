package org.wordpress.android.ui.mediapicker.insert

import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.analytics.AnalyticsTracker.Stat.STOCK_MEDIA_UPLOADED
import org.wordpress.android.fluxc.model.MediaModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.StockMediaStore
import org.wordpress.android.fluxc.store.StockMediaUploadItem
import org.wordpress.android.ui.mediapicker.MediaItem.Identifier
import org.wordpress.android.ui.mediapicker.MediaItem.Identifier.StockMediaIdentifier
import org.wordpress.android.ui.mediapicker.insert.MediaInsertUseCase.MediaInsertResult
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.MEDIA
import java.util.HashMap
import javax.inject.Inject

class StockMediaInsertUseCase(
    private val site: SiteModel,
    private val stockMediaStore: StockMediaStore
): MediaInsertUseCase {
    override suspend fun insert(identifiers: List<Identifier>): MediaInsertResult {
        val result = stockMediaStore.performUploadStockMedia(site, identifiers.mapNotNull { identifier ->
            (identifier as? StockMediaIdentifier)?.let {
                StockMediaUploadItem(it.name, it.title, it.url)
            }
        })
        return when {
            result.error != null -> MediaInsertResult.Failure(result.error.message)
            else -> {
                trackUploadedStockMediaEvent(result.mediaList)
                MediaInsertResult.Success(result.mediaList.mapNotNull { Identifier.RemoteId(it.mediaId) })
            }
        }
    }

    private fun trackUploadedStockMediaEvent(mediaList: List<MediaModel>) {
        if (mediaList.isEmpty()) {
            AppLog.e(MEDIA, "Cannot track uploaded stock media event if mediaList is empty")
            return
        }
        val isMultiselect = mediaList.size > 1
        val properties: MutableMap<String, Any?> = HashMap()
        properties["is_part_of_multiselection"] = isMultiselect
        properties["number_of_media_selected"] = mediaList.size
        AnalyticsTracker.track(STOCK_MEDIA_UPLOADED, properties)
    }

    class StockMediaInsertUseCaseFactory
    @Inject constructor(
        private val stockMediaStore: StockMediaStore
    ) {
        fun build(site: SiteModel): StockMediaInsertUseCase {
            return StockMediaInsertUseCase(site, stockMediaStore)
        }
    }
}