package com.michaldrabik.network.di

import com.michaldrabik.network.Cloud
import com.michaldrabik.network.di.module.RetrofitModule
import com.michaldrabik.network.di.module.TraktModule
import com.michaldrabik.network.di.module.TvdbModule
import dagger.Component

@Component(modules = [RetrofitModule::class, TraktModule::class, TvdbModule::class])
interface CloudComponent {

  fun provideCloud(): Cloud
}