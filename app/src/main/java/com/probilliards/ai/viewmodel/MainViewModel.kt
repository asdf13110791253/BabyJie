package com.probilliards.ai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.probilliards.ai.ai.ShotRecommender
import com.probilliards.ai.vision.BallDetector
import com.probilliards.ai.vision.PocketDetector
import com.probilliards.ai.vision.TableDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tableDetector: TableDetector,
    private val ballDetector: BallDetector,
    private val pocketDetector: PocketDetector,
    private val shotRecommender: ShotRecommender
) : ViewModel()
