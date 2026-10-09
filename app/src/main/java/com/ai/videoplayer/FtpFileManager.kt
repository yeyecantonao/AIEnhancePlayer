package com.ai.videoplayer
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile

data class FtpItem(val name:String,val size:Long,val time:Long,val isDir:Boolean)
class FtpFileManager {
    private var ftpClient:FTPClient?=null
    fun connect(ip:String,port:Int,user:String,pwd:String):Boolean{
        ftpClient = FTPClient()
        return try{
            ftpClient?.connect(ip,port)
            ftpClient?.login(user,pwd)
            ftpClient?.enterLocalPassiveMode()
            ftpClient?.setFileType(FTP.BINARY_FILE_TYPE)
            true
        }catch(e:Exception){
            e.printStackTrace()
            false
        }
    }
    fun listFolder(path:String,sortType:String):List<FtpItem>{
        val items = mutableListOf<FtpItem>()
        val files = ftpClient?.listFiles(path) ?: return emptyList()
        for(f in files){
            items.add(FtpItem(f.name,f.size,f.timestamp.timeInMillis,f.isDirectory))
        }
        when(sortType){
            "time"-> items.sortByDescending { it.time }
            "size"-> items.sortByDescending { it.size }
            else-> items.sortBy { it.name }
        }
        return items
    }
    fun disconnect(){
        try{
            ftpClient?.logout()
            ftpClient?.disconnect()
        }catch (_:Exception){}
    }
}
