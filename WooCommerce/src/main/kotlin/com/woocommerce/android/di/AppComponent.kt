package com.woocommerce.android.di

import android.app.Application
import com.woocommerce.android.WooCommerce
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import org.wordpress.android.fluxc.module.ReleaseBaseModule
import org.wordpress.android.fluxc.module.ReleaseNetworkModule
import org.wordpress.android.fluxc.module.ReleaseOkHttpClientModule
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(
        AndroidSupportInjectionModule::class,
        ApplicationModule::class,
        AppSecretsModule::class,
        ReleaseBaseModule::class,
        ReleaseNetworkModule::class,
        ReleaseOkHttpClientModule::class,
        ActivityBindingModule::class))
interface AppComponent : AndroidInjector<WooCommerce> {
    override fun inject(app: WooCommerce)

    // Allows us to inject the application without having to instantiate any modules, and provides the Application
    // in the app graph
    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        fun build(): AppComponent
    }
}
