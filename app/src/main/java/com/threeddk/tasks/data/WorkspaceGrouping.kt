package com.threeddk.tasks.data

// Only the known QA naming convention is grouped; no server data is moved.
fun isTestWorkspace(name:String)=Regex("^3DDK\\s*•\\s*Kiểm thử P0 [0-9a-f]{8}$",RegexOption.IGNORE_CASE).matches(name.trim())
