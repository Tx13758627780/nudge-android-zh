package com.astraedus.nudge.di

import com.astraedus.nudge.domain.nuke.NukeEnforcement
import com.astraedus.nudge.service.NukeEnforcementSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NukeModule {
    @Binds
    abstract fun bindNukeEnforcement(impl: NukeEnforcementSource): NukeEnforcement
}
