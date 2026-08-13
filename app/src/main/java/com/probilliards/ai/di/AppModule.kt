// File: app/src/main/java/com/probilliards/ai/di/AppModule.kt
package com.probilliards.ai.di

import com.probilliards.ai.ai.ShotRecommender
import com.probilliards.ai.vision.BallDetector
import com.probilliards.ai.vision.PocketDetector
import com.probilliards.ai.vision.TableDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideTableDetector(): TableDetector {
        return TableDetector()
    }
    
    @Provides
    @Singleton
    fun provideBallDetector(): BallDetector {
        return BallDetector()
    }
    
    @Provides
    @Singleton
    fun providePocketDetector(): PocketDetector {
        return PocketDetector()
    }
    
    @Provides
    @Singleton
    fun provideShotRecommender(): ShotRecommender {
        return ShotRecommender()
    }
}
