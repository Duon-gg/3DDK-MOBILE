# Retrofit/Gson DTO fields are part of the REST contract.
-keep class com.threeddk.tasks.data.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault


-keep class com.threeddk.tasks.model.** { *; }
-keep class com.threeddk.tasks.viewmodel.TaskEditorState { *; }
