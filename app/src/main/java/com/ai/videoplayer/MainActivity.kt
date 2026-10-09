package com.ai.videoplayer
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.ai.videoplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val ftpManager = FtpFileManager()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //滤镜选择下拉
        val filterList = arrayOf("原始画面","FSR超分","DLSS风格光影增强","锐化降噪","Anime4K动漫增强")
        binding.spFilter.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,filterList)

        //排序下拉
        val sortList = arrayOf("按名称","按时间","按大小")
        binding.spSort.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,sortList)

        //连接FTP按钮
        binding.btnConnectFtp.setOnClickListener {
            val ip = binding.etIp.text.toString()
            val port = binding.etPort.text.toString().toInt()
            val user = binding.etUser.text.toString()
            val pwd = binding.etPwd.text.toString()
            val ok = ftpManager.connect(ip,port,user,pwd)
            if(ok){
                loadFtpList()
            }
        }
        //打开播放器
        binding.btnPlay.setOnClickListener {
            val intent = Intent(this,PlayerActivity::class.java)
            intent.putExtra("filterIndex",binding.spFilter.selectedItemPosition)
            startActivity(intent)
        }
    }
    fun loadFtpList(){
        val sortKey = when(binding.spSort.selectedItemPosition){
            1->"time"
            2->"size"
            else->"name"
        }
        val list = ftpManager.listFolder("/",sortKey)
        //展示ftp文件列表到ListView
    }
}
