package rep.trace.time

object timeType extends Enumeration{
    val preblock_start = 10
    val preblock_end = 20
    val preload_start = 30
    val preload_recv_start = 31
    val preload_recv_end = 32
    val preload_end = 40
    val endorse_start = 50
    val endorse_recv_start = 51
    val endorse_recv_end = 52
    val endorse_end = 60
    val sendblock_start = 70
    val sendblock_end = 80
    val store_start  = 90
    val store_end = 100
}