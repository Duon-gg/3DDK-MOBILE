package com.threeddk.tasks
import com.threeddk.tasks.viewmodel.AppViewModel
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.*
import com.google.android.material.snackbar.Snackbar
import com.threeddk.tasks.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
class MainActivity:AppCompatActivity(){
 private lateinit var binding:ActivityMainBinding
 private lateinit var nav:NavController
 private val vm:AppViewModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);WindowCompat.setDecorFitsSystemWindows(window,false)
  binding=ActivityMainBinding.inflate(layoutInflater);setContentView(binding.root)
  ViewCompat.setOnApplyWindowInsetsListener(binding.root){view,insets->val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime());view.updatePadding(top=bars.top,bottom=bars.bottom);insets}
  setSupportActionBar(binding.toolbar)
  nav=(supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
  setupActionBarWithNavController(nav,AppBarConfiguration(setOf(R.id.auth,R.id.workspaces,R.id.invitations)))
  binding.bottomNav.setupWithNavController(nav)
  nav.addOnDestinationChangedListener{_,destination,args->
   updateNavigation()
   binding.toolbar.setBackgroundColor(getColor(
    if(destination.id==R.id.dataTable) R.color.mobile_table_toolbar else R.color.mobile_surface
   ))
   if(destination.id==R.id.recordEditor){
    val id=args?.getString("recordId") ?: "new"
    if(id=="new"||id.startsWith("new/"))supportActionBar?.setTitle(R.string.p0_them_dong)
    else supportActionBar?.title=""
    binding.toolbar.setNavigationIcon(R.drawable.ic_close)
    binding.toolbar.setNavigationContentDescription(R.string.record_close)
   }
  }
  receiveIntent(intent)
  lifecycleScope.launch{repeatOnLifecycle(Lifecycle.State.STARTED){
   launch{vm.session.collect{state->
    updateNavigation()
    if(state=="READY"){
     if(nav.currentDestination?.id==R.id.auth)nav.navigate(R.id.workspaces,null,navOptions{popUpTo(R.id.auth){inclusive=true}})
     openPending()
    }else if(nav.currentDestination?.id!=R.id.auth)nav.navigate(R.id.auth,null,navOptions{popUpTo(nav.graph.id){inclusive=true}})
   }}
   launch{vm.loading.collect{binding.progress.isVisible=it}}
   launch{vm.message.collect{it?.let{message->Snackbar.make(binding.root,message,Snackbar.LENGTH_LONG).show();vm.message.value=null}}}
  }}
 }
 private fun updateNavigation(){binding.toolbar.isVisible=nav.currentDestination?.id !in setOf(R.id.workspaces,R.id.auth);binding.bottomNav.isVisible=vm.session.value=="READY"&&nav.currentDestination?.id ==R.id.invitations}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);receiveIntent(intent);openPending()}
 private fun receiveIntent(intent:Intent){intent.getStringExtra("taskId")?.let{if(runCatching{java.util.UUID.fromString(it)}.isSuccess)vm.pendingTaskId=it};intent.removeExtra("taskId")}
 private fun openPending(){if(vm.session.value=="READY"&&vm.pendingTaskId!=null){vm.pendingTaskId=null;vm.message.value="Thông báo này thuộc phiên bản công việc trước."}}
 override fun onSupportNavigateUp()=nav.navigateUp()||super.onSupportNavigateUp()
 override fun onResume(){super.onResume();if(vm.session.value=="READY")vm.refresh()}
}

