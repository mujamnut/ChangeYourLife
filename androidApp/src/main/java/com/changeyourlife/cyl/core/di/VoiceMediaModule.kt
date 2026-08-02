package com.changeyourlife.cyl.core.di

import com.changeyourlife.cyl.data.media.AndroidVoiceRecorder
import com.changeyourlife.cyl.data.media.Media3ChatAudioPlayer
import com.changeyourlife.cyl.domain.repository.ChatAudioPlayer
import com.changeyourlife.cyl.domain.repository.VoiceRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceMediaModule {
    @Binds
    @Singleton
    abstract fun bindVoiceRecorder(implementation: AndroidVoiceRecorder): VoiceRecorder

    @Binds
    @Singleton
    abstract fun bindChatAudioPlayer(implementation: Media3ChatAudioPlayer): ChatAudioPlayer
}
