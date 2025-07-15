package com.ibbie.catrec_gamingscreenrecorder

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"
    // Use Google's test ad unit for rewarded ads
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var lastLoadContext: Context? = null

    fun initialize(context: Context) {
        MobileAds.initialize(context)
    }

    fun loadRewardedAd(context: Context, onLoaded: (() -> Unit)? = null, onFailed: ((String) -> Unit)? = null) {
        if (isLoading) return
        isLoading = true
        lastLoadContext = context
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    rewardedAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w(TAG, "Failed to load rewarded ad: ${error.message}")
                    rewardedAd = null
                    isLoading = false
                    onFailed?.invoke(error.message ?: "Unknown error")
                }
            }
        )
    }

    fun showRewardedAd(activity: Activity, onReward: (() -> Unit)? = null, onClosed: (() -> Unit)? = null, onFailed: (() -> Unit)? = null) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad not loaded")
            onFailed?.invoke()
            // Optionally, try to load again
            lastLoadContext?.let { loadRewardedAd(it) }
            return
        }
        ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                onClosed?.invoke()
                // Preload next ad
                lastLoadContext?.let { loadRewardedAd(it) }
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "Failed to show rewarded ad: ${error.message}")
                rewardedAd = null
                onFailed?.invoke()
                lastLoadContext?.let { loadRewardedAd(it) }
            }
        }
        ad.show(activity) { rewardItem: RewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onReward?.invoke()
        }
    }
} 