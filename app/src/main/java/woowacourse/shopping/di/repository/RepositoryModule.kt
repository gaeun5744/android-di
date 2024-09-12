package woowacourse.shopping.di.repository

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.woowa.di.injection.DIModule
import com.woowa.di.injection.Module
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

@DIModule
class RepositoryModule private constructor() :
    DefaultLifecycleObserver,
    Module<RepositoryModule, RepositoryDI> {
        private lateinit var repositoryMap: List<Pair<String, KFunction<*>>>
        private lateinit var repositoryBinder: RepositoryBinder

        override fun onCreate(owner: LifecycleOwner) {
            super.onCreate(owner)
            repositoryBinder = RepositoryBinder()
            repositoryMap = createRepositoryMap()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            instance = null
            owner.lifecycle.removeObserver(this)
        }

        private fun createRepositoryMap(): List<Pair<String, KFunction<*>>> {
            return RepositoryBinder::class.declaredMemberFunctions
                .filter { it.returnType.jvmErasure.isSubclassOf(RepositoryDI::class) }
                .map { kFunction ->
                    val key =
                        kFunction.returnType.jvmErasure.simpleName
                            ?: error("$kFunction 의 key값을 지정할 수 없습니다.")
                    key to kFunction
                }
        }

        override fun getDIInstance(type: KClass<out RepositoryDI>): RepositoryDI {
            val kFunction =
                instance?.repositoryMap?.find { it.first == type.simpleName }?.second
                    ?: error("${type.simpleName} 해당 interface에 대한 객체가 없습니다.")
            return kFunction.call(repositoryBinder) as RepositoryDI
        }

        override fun getDIInstance(
            type: KClass<out RepositoryDI>,
            qualifier: KClass<out Annotation>,
        ): RepositoryDI {
            val kFunction =
                instance?.repositoryMap?.find { it.first == type.simpleName && it.second.annotations.any { it.annotationClass == qualifier } }?.second
                    ?: error("${type.simpleName} 해당 interface에 대한 객체가 없습니다.")
            return kFunction.call(repositoryBinder) as RepositoryDI
        }

        companion object {
            private var instance: RepositoryModule? = null

            fun initLifeCycle(context: Context) {
                require(instance == null) {
                    "Module은 한번만 초기화가 가능합니다."
                }

                val newInstance = RepositoryModule()
                when (context) {
                    is Application -> ProcessLifecycleOwner.get().lifecycle.addObserver(newInstance)
                    is AppCompatActivity -> context.lifecycle.addObserver(newInstance)
                    is Fragment -> context.lifecycle.addObserver(newInstance)
                    else -> error("해당 로직은 Application, AppCompatActivity, Fragment에서만 쓸 수 있습니다.")
                }
                instance = newInstance
            }

            fun getInstance(): RepositoryModule = instance ?: error("Module에 대한 lifecycle이 지정되지 않았습니다.")

            fun getInstanceOrNull(): RepositoryModule? = instance
        }
    }
