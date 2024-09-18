package com.woowa.di.viewmodel

import com.woowa.di.ApplicationContext
import com.woowa.di.component.Component
import com.woowa.di.component.DIBuilder
import com.woowa.di.findQualifierClassOrNull
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinProperty

class ViewModelComponent<binder : Any> private constructor(private val binderClazz: KClass<binder>) :
    Component {
        private val binderInstance: binder = binderClazz.createInstance()
        private val binderKFunc: Map<String, KFunction<*>> = createProvider()
        private val diInstances: MutableMap<String, Any?> = mutableMapOf()

        private fun createProvider(): Map<String, KFunction<*>> {
            return binderClazz.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }
                .associate { kFunc ->
                    val key =
                        kFunc.returnType.jvmErasure.simpleName
                            ?: error("익명 객체와 같이, 이름이 없는 객체는 di 주입을 할 수 없습니다.")

                    kFunc.findQualifierClassOrNull()?.let { qualifier ->
                        return@associate (key + qualifier.simpleName) to kFunc
                    }

                    return@associate key to kFunc
                }
        }

        override fun getDIInstance(
            type: KClass<*>,
            qualifier: KClass<out Annotation>?,
        ): Any? {
            require(!isAlreadyCreatedDI(type, qualifier)) {
                "한 객체는 하나의 viewModel에 대해서만 생성할 수 있습니다."
            }
            qualifier?.let {
                return diInstances.getOrPut((type.simpleName + it.simpleName)) {
                    createDIInstance(type, qualifier)
                }
            }
            return diInstances.getOrPut(
                type.simpleName ?: error("익명 객체와 같이, 이름이 없는 객체는 di 주입을 할 수 없습니다."),
            ) {
                createDIInstance(type)
            }
        }

        fun deleteDIInstance(
            type: KClass<*>,
            qualifier: KClass<out Annotation>?,
        ) {
            qualifier?.let {
                diInstances.remove(type.simpleName + qualifier.simpleName)
                return
            }
            diInstances.remove(type.simpleName)
        }

        private fun createDIInstance(
            type: KClass<*>,
            qualifier: KClass<out Annotation>? = null,
        ): Any? {
            val kFunc =
                if (qualifier != null) {
                    binderKFunc[(type.simpleName + qualifier.simpleName)]
                } else {
                    binderKFunc[type.simpleName]
                }

            val instance =
                if (requireNotNull(kFunc).parameters.any { it.hasAnnotation<ApplicationContext>() }) {
                    kFunc.call(binderInstance, arrayOf(DIBuilder.applicationContext))
                } else {
                    kFunc.call(binderInstance)
                }

            val fields =
                instance!!::class.java.declaredFields.onEach { field ->
                    field.isAccessible = true
                }.filter { it.isAnnotationPresent(Inject::class.java) }

            fields.map { field ->
                val fieldInstance =
                    ViewModelComponentManager.getDIInstance(field.type.kotlin, field.kotlinProperty?.findQualifierClassOrNull())
                field.set(instance, fieldInstance)
            }

            return instance
        }

        private fun isAlreadyCreatedDI(
            type: KClass<*>,
            qualifier: KClass<out Annotation>? = null,
        ) = diInstances[(type.simpleName + qualifier?.simpleName)] != null || diInstances[type.simpleName] != null

        companion object {
            private val instances = mutableMapOf<KClass<*>, ViewModelComponent<*>>()

            fun <binder : Any> getInstance(binderClazz: KClass<binder>): ViewModelComponent<binder> {
                return instances.getOrPut(binderClazz) {
                    ViewModelComponent(binderClazz)
                } as ViewModelComponent<binder>
            }
        }
    }
