package com.threeddk.tasks

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MvvmBoundaryTest {
 @Test fun viewsCannotAccessRepositoriesOrFindViewsManually(){
  val source=File("src/main/java/com/threeddk/tasks")
  assertTrue("Run from the app Gradle module",source.isDirectory)
  val views=File(source,"ui").walkTopDown().filter{it.extension=="kt"}.toList()+File(source,"MainActivity.kt")
  views.forEach{file->
   val text=file.readText()
   assertFalse("${file.name}: use View Binding",text.contains("findViewById"))
   assertFalse("${file.name}: data access belongs in ViewModel",Regex("import com\\.threeddk\\.tasks\\.repository\\.|\\b(?:vm|account)\\.repo\\b|\\.dao\\b").containsMatchIn(text))
  }
 }
 @Test fun requiredLayersAndAdapterBindingsExist(){
  val source=File("src/main/java/com/threeddk/tasks")
  listOf("ui","viewmodel","model","repository","adapter").forEach{assertTrue("Missing package $it",File(source,it).isDirectory)}
  File(source,"adapter").listFiles()!!.filter{it.extension=="kt"}.forEach{file->
   assertTrue("${file.name}: bind XML rows with View Binding",file.readText().contains("Binding"))
  }
 }
}
