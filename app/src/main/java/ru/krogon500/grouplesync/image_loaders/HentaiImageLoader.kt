package ru.krogon500.grouplesync.image_loaders

import com.nostra13.universalimageloader.core.ImageLoader

class HentaiImageLoader : ImageLoader() {
    companion object {
        @Volatile
        private var instance: HentaiImageLoader? = null

        /** Returns singletone class instance  */
        fun getInstance(): HentaiImageLoader? {
            if (HentaiImageLoader.instance == null) {
                synchronized(ImageLoader::class.java) {
                    if (HentaiImageLoader.instance == null) {
                        HentaiImageLoader.instance = HentaiImageLoader()
                    }
                }
            }
            return HentaiImageLoader.instance
        }
    }
}
