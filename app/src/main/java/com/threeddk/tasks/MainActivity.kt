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
     openPending();askNotificationPermission()
    }else if(nav.currentDestination?.id!=R.id.auth)nav.navigate(R.id.auth,null,navOptions{popUpTo(nav.graph.id){inclusive=true}})
   }}
   launch{vm.loading.collect{binding.progress.isVisible=it}}
   launch{vm.message.collect{it?.let{message->Snackbar.make(binding.root,message,Snackbar.LENGTH_LONG).show();vm.message.value=null}}}
  }}
 }
 private fun updateNavigation(){binding.toolbar.isVisible=nav.currentDestination?.id !in setOf(R.id.workspaces,R.id.auth);binding.bottomNav.isVisible=vm.session.value=="READY"&&nav.currentDestination?.id ==R.id.invitations}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);receiveIntent(intent);openPending()
  // Invite link opened while another screen is up: go back to home, which asks to join on resume.
  if(intent.data?.scheme=="threeddk"&&vm.session.value=="READY")nav.popBackStack(R.id.workspaces,false)
 }
 private var pendingRecord:Pair<String,String>?=null
 private val notificationPermission=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){}
 /** Android 13+ hides every push until the user allows it; ask once after sign-in, like most apps. */
 private fun askNotificationPermission(){
  if(android.os.Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)return
  val prefs=getSharedPreferences("app",MODE_PRIVATE);if(prefs.getBoolean("askedNotifications",false))return
  prefs.edit().putBoolean("askedNotifications",true).apply();notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
 }
 private fun receiveIntent(intent:Intent){
  val table=intent.getStringExtra("tableId");val record=intent.getStringExtra("recordId")
  if(table!=null&&record!=null&&listOf(table,record).all{runCatching{java.util.UUID.fromString(it)}.isSuccess})pendingRecord=table to record
  intent.removeExtra("tableId");intent.removeExtra("recordId")
  intent.getStringExtra("taskId")?.let{if(runCatching{java.util.UUID.fromString(it)}.isSuccess)vm.pendingTaskId=it};intent.removeExtra("taskId")}
 private fun openPending(){
  // A tapped @mention push opens that record's comments, like Airtable's notification links.
  pendingRecord?.takeIf{vm.session.value=="READY"}?.let{(table,record)->pendingRecord=null;nav.navigate(R.id.recordEditor,androidx.core.os.bundleOf("path" to "tables/$table","recordId" to record))}
  if(vm.session.value=="READY"&&vm.pendingTaskId!=null){vm.pendingTaskId=null;vm.message.value="Thông báo này thuộc phiên bản công việc trước."}}
 override fun onSupportNavigateUp()=nav.navigateUp()||super.onSupportNavigateUp()
 /** Airtable-style chrome: toolbar and system-bar strips take a base's tile colour (bg to text); null restores defaults. */
 fun paintChrome(tile:Pair<Int,Int>?){
  val toolbar=binding.toolbar
  val ink=tile?.second?:com.google.android.material.color.MaterialColors.getColor(toolbar,com.google.android.material.R.attr.colorOnSurface)
  val surface=com.google.android.material.color.MaterialColors.getColor(toolbar,com.google.android.material.R.attr.colorSurface)
  toolbar.setBackgroundColor(tile?.first?:android.graphics.Color.TRANSPARENT);toolbar.setTitleTextColor(ink)
  // The up arrow is a DrawerArrowDrawable that paints with its own colour and ignores tint; NavController swaps it after view creation.
  val paintIcons=Runnable{(toolbar.navigationIcon as? androidx.appcompat.graphics.drawable.DrawerArrowDrawable)?.color=ink;toolbar.overflowIcon?.setTint(ink);Unit}
  paintIcons.run();toolbar.post(paintIcons)
  @Suppress("DEPRECATION") window.statusBarColor=tile?.first?:surface
  binding.root.setBackgroundColor(tile?.first?:surface);binding.navHost.setBackgroundColor(surface)
  WindowCompat.getInsetsController(window,window.decorView).apply{val light=tile==null||tile.second!=android.graphics.Color.WHITE;isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light}
 }
 override fun onResume(){super.onResume();if(vm.session.value=="READY")vm.refresh()}
}

