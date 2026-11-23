package com.jagadeesh.bolttisland

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("boltt_shift", Context.MODE_PRIVATE) }
    private var tab = "CALENDAR"
    private var visibleMonth = YearMonth.now()
    private var selectedDate = LocalDate.now()
    private val bg = Color.rgb(10,15,29)
    private val panel = Color.rgb(22,30,49)
    private val muted = Color.rgb(157,173,197)
    private val accent = Color.rgb(119,107,255)
    private val mint = Color.rgb(67,214,170)
    private val gold = Color.rgb(246,190,91)
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg; window.navigationBarColor=bg
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),44)
        AlarmScheduler.rescheduleAll(this); render()
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun shape(c:Int,r:Int=18)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
    private fun t(s:String,z:Float=14f,c:Int=Color.WHITE,b:Boolean=false)=TextView(this).apply{text=s;textSize=z;setTextColor(c);if(b)setTypeface(typeface,Typeface.BOLD)}
    private fun v()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun h()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun gap(n:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(n))}
    private fun card()=v().apply{setPadding(dp(15),dp(14),dp(15),dp(14));background=shape(panel,20)}
    private fun btn(s:String,p:Boolean=false)=t(s,13f,Color.WHITE,true).apply{gravity=Gravity.CENTER;setPadding(dp(10),dp(11),dp(10),dp(11));background=shape(if(p)accent else Color.rgb(42,52,75),14);isClickable=true;isFocusable=true}
    private val months=arrayOf("జనవరి","ఫిబ్రవరి","మార్చి","ఏప్రిల్","మే","జూన్","జూలై","ఆగస్టు","సెప్టెంబర్","అక్టోబర్","నవంబర్","డిసెంబర్")
    private fun shiftType()=prefs.getString("shift_type","DAY")?:"DAY"
    private fun shiftStart()=prefs.getString("shift_start","07:00")?:"07:00"
    private fun shiftEnd()=prefs.getString("shift_end","19:00")?:"19:00"
    private fun eventData()=try{JSONArray(prefs.getString("calendar_events","[]"))}catch(_:Exception){JSONArray()}
    private fun saveEvents(a:JSONArray){prefs.edit().putString("calendar_events",a.toString()).apply()}
    private fun events(date:LocalDate):List<JSONObject>{val a=eventData();return(0 until a.length()).mapNotNull{a.optJSONObject(it)}.filter{it.optString("date")==date.toString()}}
    private fun render(){
        val root=v().apply{setBackgroundColor(bg)}
        val head=v().apply{setPadding(dp(19),dp(17),dp(19),dp(10))}
        val brand=h();brand.addView(t("BOLTT",21f,Color.WHITE,true));brand.addView(t(" SHIFT",21f,mint,true));brand.addView(View(this),LinearLayout.LayoutParams(0,1,1f));brand.addView(t("SHIFT • LIFE • TIME",10f,muted,true))
        head.addView(brand);head.addView(t("Your personal shift calendar",12f,muted));root.addView(head)
        val scroll=ScrollView(this).apply{clipToPadding=false;setPadding(dp(14),dp(3),dp(14),dp(12))}
        content=v();scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=h().apply{setPadding(dp(6),dp(7),dp(6),dp(8));setBackgroundColor(Color.rgb(14,21,37))}
        listOf("CALENDAR" to "Calendar","ALARMS" to "Alarms","SHIFT" to "Shift","ROUTINE" to "Routine").forEach{(key,label)->
            nav.addView(t(label,11f,if(tab==key)Color.WHITE else muted,tab==key).apply{gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(12));if(tab==key)background=shape(Color.rgb(55,53,105),13);setOnClickListener{tab=key;render()}},LinearLayout.LayoutParams(0,dp(46),1f))
        }
        root.addView(nav);setContentView(root)
        when(tab){"CALENDAR"->calendarPage();"ALARMS"->alarmsPage();"SHIFT"->shiftPage();else->routinePage()}
    }

    private fun calendarPage(){
        val today=LocalDate.now()
        val top=h();top.addView(t("MONTH VIEW",11f,Color.rgb(174,164,255),true));top.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        top.addView(btn("TODAY").apply{setOnClickListener{selectedDate=today;visibleMonth=YearMonth.from(today);render()}})
        content.addView(top);content.addView(gap(9))
        val cal=card();val mh=h()
        mh.addView(btn("‹").apply{setOnClickListener{visibleMonth=visibleMonth.minusMonths(1);selectedDate=visibleMonth.atDay(1);render()}},LinearLayout.LayoutParams(dp(44),dp(42)))
        val title=v().apply{gravity=Gravity.CENTER}
        title.addView(t(visibleMonth.month.getDisplayName(TextStyle.FULL,Locale.ENGLISH).uppercase(),16f,Color.WHITE,true).apply{gravity=Gravity.CENTER})
        title.addView(t(months[visibleMonth.monthValue-1]+"  "+visibleMonth.year,12f,muted).apply{gravity=Gravity.CENTER})
        mh.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        mh.addView(btn("›").apply{setOnClickListener{visibleMonth=visibleMonth.plusMonths(1);selectedDate=visibleMonth.atDay(1);render()}},LinearLayout.LayoutParams(dp(44),dp(42)))
        cal.addView(mh);cal.addView(gap(10))
        val weekdays=h();listOf("ఆది","సోమ","మంగళ","బుధ","గురు","శుక్ర","శని").forEachIndexed{i,s->weekdays.addView(t(s,10f,if(i==0||i==6)gold else muted,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(28),1f))}
        cal.addView(weekdays)
        val offset=visibleMonth.atDay(1).dayOfWeek.value%7
        val cells=((offset+visibleMonth.lengthOfMonth()+6)/7)*7
        var cell=0
        while(cell<cells){
            val row=h()
            for(col in 0..6){
                val day=cell-offset+1
                if(day in 1..visibleMonth.lengthOfMonth()){
                    val date=visibleMonth.atDay(day);val chosen=date==selectedDate;val isToday=date==today;val has=events(date).isNotEmpty()
                    val box=v().apply{gravity=Gravity.CENTER;setPadding(0,dp(5),0,dp(3));if(chosen)background=shape(accent,12) else if(isToday)background=shape(Color.rgb(39,55,74),12);setOnClickListener{selectedDate=date;render()}}
                    box.addView(t(day.toString(),14f,if(chosen)Color.WHITE else if(isToday)mint else Color.rgb(232,237,247),chosen||isToday).apply{gravity=Gravity.CENTER})
                    box.addView(t(if(has)"•" else " ",10f,gold,true).apply{gravity=Gravity.CENTER})
                    row.addView(box,LinearLayout.LayoutParams(0,dp(48),1f))
                }else row.addView(View(this),LinearLayout.LayoutParams(0,dp(48),1f))
                cell++
            }
            cal.addView(row)
        }
        cal.addView(gap(8));cal.addView(t("● Today     • Event saved",10f,muted));content.addView(cal);content.addView(gap(12))

        val day=card()
        day.addView(t(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")),18f,Color.WHITE,true))
        day.addView(t(selectedDate.dayOfMonth.toString()+" "+months[selectedDate.monthValue-1]+" "+selectedDate.year,12f,muted));day.addView(gap(9))
        val shift=if(selectedDate==today)shiftType() else prefs.getString("shift_"+selectedDate,"NOT_SET")?:"NOT_SET"
        val shiftText=when(shift){"DAY"->"☀ DAY SHIFT  "+shiftStart()+"–"+shiftEnd();"NIGHT"->"☾ NIGHT SHIFT  "+shiftStart()+"–"+shiftEnd();"OFF"->"✦ WEEK OFF";else->"SHIFT NOT PLANNED"}
        day.addView(t(shiftText,13f,mint,true));day.addView(gap(10))
        val actions=h()
        actions.addView(btn("＋ Add event",true).apply{setOnClickListener{addEvent()}},LinearLayout.LayoutParams(0,dp(43),1f))
        actions.addView(View(this),LinearLayout.LayoutParams(dp(7),1))
        actions.addView(btn("Set shift").apply{setOnClickListener{setDateShift()}},LinearLayout.LayoutParams(0,dp(43),1f))
        day.addView(actions);content.addView(day);content.addView(gap(11))

        val agenda=card();agenda.addView(t("DAY AGENDA",11f,Color.rgb(174,164,255),true));agenda.addView(gap(7))
        val list=events(selectedDate)
        if(list.isEmpty())agenda.addView(t("No events yet. Add a work task, appointment, payment or personal event.",13f,muted))
        list.forEach{e->val r=h();r.addView(t(e.optString("time","All day"),13f,mint,true),LinearLayout.LayoutParams(dp(75),-2));r.addView(t(e.optString("title"),14f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f));r.addView(t("×",20f,muted).apply{setOnClickListener{removeEvent(e.optLong("id"));render()}});agenda.addView(r);agenda.addView(gap(6))}
        content.addView(agenda);content.addView(gap(11))

        teluguPanchangam()
        content.addView(gap(11))
        val quick=card();quick.addView(t("QUICK ACTIONS",11f,Color.rgb(174,164,255),true));quick.addView(gap(8))
        val q=h()
        q.addView(btn("⏰ Alarms").apply{setOnClickListener{tab="ALARMS";render()}},LinearLayout.LayoutParams(0,dp(43),1f))
        q.addView(View(this),LinearLayout.LayoutParams(dp(6),1))
        q.addView(btn("🍱 Meals").apply{setOnClickListener{addMealSet()}},LinearLayout.LayoutParams(0,dp(43),1f))
        q.addView(View(this),LinearLayout.LayoutParams(dp(6),1))
        q.addView(btn("✓ Tasks").apply{setOnClickListener{tab="ROUTINE";render()}},LinearLayout.LayoutParams(0,dp(43),1f))
        quick.addView(q);content.addView(quick)
    }

    private fun teluguPanchangam(){
        val c=card();c.addView(t("తెలుగు పంచాంగం  •  TELUGU PANCHANGAM",12f,gold,true));c.addView(gap(7))
        if(selectedDate==LocalDate.of(2026,11,23)){
            c.addView(t("శ్రీ పరాభవ నామ సంవత్సరం  •  కార్తీక మాసం",14f,Color.WHITE,true))
            c.addView(t("శుక్ల పక్షం  •  సోమవారం",12f,muted));c.addView(gap(8))
            pLine(c,"తిథి","శుక్ల చతుర్దశి • రా. 11:42 వరకు")
            pLine(c,"నక్షత్రం","భరణి • 24 Nov ఉ. 2:02 వరకు")
            pLine(c,"యోగం","వరీయాన్ • రా. 7:58 వరకు")
            pLine(c,"సూర్యోదయం / అస్తమయం","ఉ. 6:14 / సా. 5:40")
            pLine(c,"రాహుకాలం","ఉ. 7:40 – 9:06")
            c.addView(t("Srikalahasti reference timings • IST",10f,muted))
        }else{
            c.addView(t("ఎంచుకున్న తేదీకి తిథి, నక్షత్రం, యోగం, కరణం మరియు శుభ సమయాలు చూడండి.",13f,Color.WHITE))
            c.addView(t("Selected date: "+selectedDate,11f,muted))
        }
        c.addView(gap(9))
        c.addView(btn("పూర్తి పంచాంగం చూడండి  ↗",true).apply{setOnClickListener{
            val url="https://vaya.so/tools/panchang?d="+selectedDate+"&l=srikalahasti"
            startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
        }})
        content.addView(c)
    }
    private fun pLine(parent:LinearLayout,a:String,b:String){val r=h();r.addView(t(a,11f,muted),LinearLayout.LayoutParams(dp(125),-2));r.addView(t(b,11f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f));parent.addView(r);parent.addView(gap(5))}
    private fun addEvent(){
        val name=EditText(this).apply{hint="Event name";setTextColor(Color.WHITE);setHintTextColor(muted)}
        val time=EditText(this).apply{hint="Time (HH:mm) or leave blank";setTextColor(Color.WHITE);setHintTextColor(muted)}
        val box=v().apply{setPadding(dp(18),0,dp(18),0);addView(name);addView(time)}
        android.app.AlertDialog.Builder(this).setTitle("Add event • "+selectedDate).setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->
            val title=name.text.toString().trim();if(title.isNotEmpty()){val a=eventData();a.put(JSONObject().put("id",System.currentTimeMillis()).put("date",selectedDate.toString()).put("title",title).put("time",time.text.toString().trim().ifBlank{"All day"}));saveEvents(a);render()}
        }.show()
    }
    private fun removeEvent(id:Long){val old=eventData();val a=JSONArray();for(i in 0 until old.length()){val o=old.optJSONObject(i)?:continue;if(o.optLong("id")!=id)a.put(o)};saveEvents(a)}
    private fun setDateShift(){
        android.app.AlertDialog.Builder(this).setTitle("Shift • "+selectedDate).setItems(arrayOf("Day shift","Night shift","Week off","Clear")){_,i->
            val value=when(i){0->"DAY";1->"NIGHT";2->"OFF";else->"NOT_SET"}
            prefs.edit().putString("shift_"+selectedDate,value).apply()
            if(selectedDate==LocalDate.now())prefs.edit().putString("shift_type",value).apply()
            render()
        }.show()
    }

    private fun alarmsPage(){
        content.addView(t("Your reminders",24f,Color.WHITE,true));content.addView(t("Daily alarms for shifts, meals and personal tasks.",13f,muted));content.addView(gap(13))
        content.addView(btn("＋ Create alarm",true).apply{setOnClickListener{addAlarmDialog()}});content.addView(gap(10))
        val all=AlarmStore.all(this).sortedBy{it.time}
        if(all.isEmpty())content.addView(emptyCard("No reminders yet","Create your first alarm or add the meal reminder set."))
        all.forEach{a->
            val c=card();val r=h();val info=v()
            info.addView(t(a.time,24f,Color.WHITE,true));info.addView(t(a.title,15f,Color.WHITE,true));info.addView(t(if(a.category=="FOOD")"MEAL REMINDER" else if(a.category=="SHIFT")"SHIFT REMINDER" else "PERSONAL",10f,muted,true))
            r.addView(info,LinearLayout.LayoutParams(0,-2,1f));r.addView(Switch(this).apply{isChecked=a.enabled;setOnCheckedChangeListener{_,checked->AlarmStore.update(this@MainActivity,a.copy(enabled=checked));render()}})
            c.addView(r);c.addView(gap(8));val actions=h()
            actions.addView(btn("Edit").apply{setOnClickListener{editAlarm(a)}},LinearLayout.LayoutParams(0,dp(40),1f));actions.addView(View(this),LinearLayout.LayoutParams(dp(7),1))
            actions.addView(btn("Delete").apply{setOnClickListener{AlarmStore.remove(this@MainActivity,a.id);render()}},LinearLayout.LayoutParams(0,dp(40),1f))
            c.addView(actions);content.addView(c);content.addView(gap(9))
        }
    }
    private fun shiftPage(){
        content.addView(t("Shift planner",24f,Color.WHITE,true));content.addView(t("Set your current roster. Times are editable.",13f,muted));content.addView(gap(13))
        val c=card();c.addView(t("SHIFT TYPE",11f,gold,true));c.addView(gap(8));val types=h()
        listOf("DAY","NIGHT","OFF").forEach{type->types.addView(btn(type,shiftType()==type).apply{setOnClickListener{prefs.edit().putString("shift_type",type).apply();render()}},LinearLayout.LayoutParams(0,dp(46),1f));if(type!="OFF")types.addView(View(this),LinearLayout.LayoutParams(dp(6),1))}
        c.addView(types);c.addView(gap(12))
        c.addView(btn("SHIFT START   "+shiftStart()).apply{setOnClickListener{pickTime(shiftStart()){prefs.edit().putString("shift_start",it).apply();render()}}});c.addView(gap(8))
        c.addView(btn("SHIFT END   "+shiftEnd()).apply{setOnClickListener{pickTime(shiftEnd()){prefs.edit().putString("shift_end",it).apply();render()}}});c.addView(gap(11))
        c.addView(btn("Create shift start & end alarms",true).apply{setOnClickListener{upsert("Shift starts",shiftStart(),"SHIFT");upsert("Shift ends",shiftEnd(),"SHIFT");requestExact();render()}})
        content.addView(c)
    }
    private fun routinePage(){
        content.addView(t("Daily routine",24f,Color.WHITE,true));content.addView(t("Checklist for workdays and rest days.",13f,muted));content.addView(gap(12))
        content.addView(btn("＋ Add task",true).apply{setOnClickListener{addTask()}});content.addView(gap(9))
        val tasks=loadTasks()
        if(tasks.length()==0)content.addView(emptyCard("Checklist clear","Add tasks like pack ID card, carry water, charge phone or prepare meals."))
        for(i in 0 until tasks.length()){
            val o=tasks.optJSONObject(i)?:continue;val id=o.optLong("id");val c=card();val r=h()
            r.addView(CheckBox(this).apply{text=o.optString("title");setTextColor(Color.WHITE);isChecked=o.optBoolean("done");buttonTintList=android.content.res.ColorStateList.valueOf(mint);setOnCheckedChangeListener{_,checked->updateTask(id,checked)}},LinearLayout.LayoutParams(0,-2,1f))
            r.addView(t("⋮",22f,muted).apply{setOnClickListener{taskOptions(id,o.optString("title"))}});c.addView(r);content.addView(c);content.addView(gap(7))
        }
        val c=card();val hours=prefs.getInt("ot_hours",0);c.addView(t("OVERTIME LOG",11f,gold,true));c.addView(t("$hours hours recorded",22f,Color.WHITE,true));c.addView(gap(8))
        val r=h();r.addView(btn("＋ 1 hour").apply{setOnClickListener{prefs.edit().putInt("ot_hours",hours+1).apply();render()}},LinearLayout.LayoutParams(0,dp(42),1f));r.addView(View(this),LinearLayout.LayoutParams(dp(7),1));r.addView(btn("Reset").apply{setOnClickListener{prefs.edit().putInt("ot_hours",0).apply();render()}},LinearLayout.LayoutParams(0,dp(42),1f));c.addView(r);content.addView(c)
    }
    private fun emptyCard(a:String,b:String):View=card().apply{addView(t(a,17f,Color.WHITE,true));addView(t(b,13f,muted))}
    private fun addAlarmDialog(){val input=EditText(this).apply{hint="Reminder name";setTextColor(Color.WHITE)};android.app.AlertDialog.Builder(this).setTitle("New reminder").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Continue"){_,_->pickTime("07:00"){AlarmStore.add(this,input.text.toString().trim().ifBlank{"Daily reminder"},it,"CUSTOM");requestExact();render()}}.show()}
    private fun editAlarm(a:ShiftAlarm){val input=EditText(this).apply{setText(a.title);setTextColor(Color.WHITE)};android.app.AlertDialog.Builder(this).setTitle("Edit reminder").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Continue"){_,_->pickTime(a.time){AlarmStore.update(this,a.copy(title=input.text.toString().trim().ifBlank{a.title},time=it));render()}}.show()}
    private fun addMealSet(){if(AlarmStore.all(this).any{it.category=="FOOD"}){tab="ALARMS";render();Toast.makeText(this,"Meal reminders already exist.",Toast.LENGTH_LONG).show();return};AlarmStore.add(this,"Breakfast / meal 1","07:30","FOOD");AlarmStore.add(this,"Lunch / meal 2","13:00","FOOD");AlarmStore.add(this,"Dinner / meal 3","20:30","FOOD");requestExact();tab="ALARMS";render()}
    private fun upsert(title:String,time:String,cat:String){val old=AlarmStore.all(this).firstOrNull{it.title==title};if(old==null)AlarmStore.add(this,title,time,cat)else AlarmStore.update(this,old.copy(time=time,enabled=true))}
    private fun requestExact(){if(Build.VERSION.SDK_INT>=31){val m=getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager;if(!m.canScheduleExactAlarms())try{startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))}catch(_:Exception){}}}
    override fun onResume(){super.onResume();AlarmScheduler.rescheduleAll(this)}
    private fun pickTime(current:String,done:(String)->Unit){val p=current.split(":");TimePickerDialog(this,{_,hh,mm->done("%02d:%02d".format(hh,mm))},p[0].toIntOrNull()?:7,p.getOrNull(1)?.toIntOrNull()?:0,true).show()}
    private fun loadTasks():JSONArray{val today=LocalDate.now().toString();val a=try{JSONArray(prefs.getString("tasks","[]"))}catch(_:Exception){JSONArray()};if(prefs.getString("task_date","")!=today){for(i in 0 until a.length())a.optJSONObject(i)?.put("done",false);prefs.edit().putString("task_date",today).apply();saveTasks(a)};return a}
    private fun saveTasks(a:JSONArray){prefs.edit().putString("tasks",a.toString()).apply()}
    private fun addTask(){val input=EditText(this).apply{hint="Task name";setTextColor(Color.WHITE)};android.app.AlertDialog.Builder(this).setTitle("Add routine task").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->val s=input.text.toString().trim();if(s.isNotEmpty()){val a=loadTasks();a.put(JSONObject().put("id",System.currentTimeMillis()).put("title",s).put("done",false));saveTasks(a);render()}}.show()}
    private fun updateTask(id:Long,done:Boolean){val a=loadTasks();for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;if(o.optLong("id")==id)o.put("done",done)};saveTasks(a)}
    private fun taskOptions(id:Long,title:String){android.app.AlertDialog.Builder(this).setTitle(title).setItems(arrayOf("Rename","Delete")){_,which->if(which==0){val input=EditText(this).apply{setText(title)};android.app.AlertDialog.Builder(this).setTitle("Rename").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->val a=loadTasks();for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;if(o.optLong("id")==id)o.put("title",input.text.toString())};saveTasks(a);render()}.show()}else{val old=loadTasks();val a=JSONArray();for(i in 0 until old.length()){val o=old.optJSONObject(i)?:continue;if(o.optLong("id")!=id)a.put(o)};saveTasks(a);render()}}.show()}
}