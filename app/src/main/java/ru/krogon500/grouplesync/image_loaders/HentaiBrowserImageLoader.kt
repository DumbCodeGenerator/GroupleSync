package ru.krogon500.grouplesync.image_loaders

import com.nostra13.universalimageloader.core.ImageLoader

class HentaiBrowserImageLoader : ImageLoader() {
    companion object {
        @Volatile
        private var instance: HentaiBrowserImageLoader? = null

        /** Returns singletone class instance  */
        fun getInstance(): HentaiBrowserImageLoader? {
            if (HentaiBrowserImageLoader.instance == null) {
                synchronized(ImageLoader::class.java) {
                    if (HentaiBrowserImageLoader.instance == null) {
                        HentaiBrowserImageLoader.instance = HentaiBrowserImageLoader()
                    }
                }
            }
            return HentaiBrowserImageLoader.instance
        }
    }
}
