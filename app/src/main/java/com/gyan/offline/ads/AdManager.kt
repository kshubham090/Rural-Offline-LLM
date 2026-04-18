package com.gyan.offline.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages AdMob interstitial ads.
 * Ads are only loaded and shown when internet is available AND user has consented.
 * Uses test ad unit IDs — replace before release.
 */
class AdManager(private val context: Context) {

    companion object {
        private const val TAG = "AdManager"

        // Replace with real ad unit ID before release
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        private const val QUERIES_PER_INTERSTITIAL = 5
    }

    private var interstitialAd: InterstitialAd? = null
    private var queryCount = 0
    private var isInitialized = false

    fun initialize(isChildDirected: Boolean = false) {
        if (isInitialized) return

        val config = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(
                if (isChildDirected) RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                else RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
            )
            .build()
        MobileAds.setRequestConfiguration(config)
        MobileAds.initialize(context) {
            isInitialized = true
            Log.i(TAG, "AdMob initialized")
            preloadInterstitial()
        }
    }

    fun onQueryCompleted(activity: Activity, isOnline: Boolean, userConsented: Boolean) {
        if (!isOnline || !userConsented) return
        queryCount++
        if (queryCount % QUERIES_PER_INTERSTITIAL == 0) {
            showInterstitial(activity)
        } else if (interstitialAd == null) {
            preloadInterstitial()
        }
    }

    private fun preloadInterstitial() {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.i(TAG, "Interstitial ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "Interstitial ad failed: ${error.message}")
                }
            })
    }

    private fun showInterstitial(activity: Activity) {
        val ad = interstitialAd ?: run {
            preloadInterstitial()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial()
            }
        }
        ad.show(activity)
    }
}
