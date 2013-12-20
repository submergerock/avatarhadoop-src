package org.apache.hadoop.hdfs.job;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.BlockingQueue;

import org.apache.hadoop.fs.FSDataInputStream;


public class BSSAPCDR implements WriteObject {
		
	byte[] start_time_s = new byte[4];
	byte[] start_time_ns = new byte[4];
	byte[] end_time_s = new byte[4];
	byte[] end_time_ns = new byte[4];
	byte[] cdr_index = new byte[4];
	byte[] cdr_type = new byte[1];
	byte[] cdr_result = new byte[1];
	byte[] base_cdr_index = new byte[4];
	byte[] base_cdr_type = new byte[1];
	byte[] tmsi = new byte[4];
	byte[] new_tmsi = new byte[4];
	byte[] imsi = new byte[8];
	byte[] imei = new byte[8];
	byte[] calling_number = new byte[8];
	byte[] called_number = new byte[8];
	byte[] third_number = new byte[24];
	byte[] mgw_ip = new byte[4];
	byte[] msc_server_ip = new byte[4];
	byte[] bsc_spc = new byte[4];
	byte[] msc_spc = new byte[4];
	byte[] lac = new byte[2];
	byte[] ci = new byte[2];
	byte[] last_lac = new byte[2];
	byte[] last_ci = new byte[2];
	byte[] cref_cause = new byte[1];
	byte[] cm_rej_cause = new byte[1];
	byte[] lu_rej_cause = new byte[1];
	byte[] assign_failure_cause = new byte[1];
	byte[] rr_cause = new byte[1];
	byte[] cip_rej_cause = new byte[1];
	byte[] disconnect_cause = new byte[1];
	byte[] cc_rel_cause = new byte[1];
	byte[] clear_cause = new byte[1];
	byte[] cp_cause = new byte[1];
	byte[] rp_cause = new byte[1];
	byte[] ho_cause = new byte[24];
	byte[] ho_failure_cause = new byte[24];
	byte[] first_paging_time = new byte[4];
	byte[] second_paging_time = new byte[4];
	byte[] third_paging_time = new byte[4];
	byte[] fourth_paging_time = new byte[4];
	byte[] cc_time = new byte[4];
	byte[] assignment_time = new byte[4];
	byte[] assignment_cmp_time = new byte[4];
	byte[] setup_time = new byte[4];
	byte[] alert_time = new byte[4];
	byte[] connect_time = new byte[4];
	byte[] disconnect_time = new byte[4];
	byte[] clear_request_time = new byte[4];
	byte[] clear_command_time = new byte[4];
	byte[] rp_data_time = new byte[4];
	byte[] rp_ack_time = new byte[4];
	byte[] aut_request_time = new byte[4];
	byte[] aut_response_time = new byte[4];
	byte[] cm_service_accept_time = new byte[4];
	byte[] call_confirm_preceding_time = new byte[4];
	byte[] connect_ack_time = new byte[4];
	byte[] smsc = new byte[24];
	byte[] sm_type = new byte[1];
	byte[] sm_data_coding_scheme = new byte[1];
	byte[] sm_length = new byte[2];
	byte[] rp_data_count = new byte[1];
	byte[] handover_count = new byte[1];
	byte[] info_trans_capability = new byte[1];
	byte[] speech_version = new byte[1];
	byte[] failed_handover_count = new byte[1];
	byte[] sub_cdr_index_set = new byte[200];//
	byte[] sub_cdr_starttime_set = new byte[200];//
	byte[] call_stop = new byte[1];
	byte[] interbsc_ho_count = new byte[1];
	byte[] pcmts = new byte[2];
	byte[] bscid = new byte[8];
	byte[] call_stop_msg = new byte[1];
	byte[] call_stop_cause = new byte[1];
	byte[] mscid = new byte[4];
	byte[] last_bscid = new byte[8];
	byte[] last_mscid = new byte[4];
	byte[] dtmf = new byte[1];
	byte[] cid = new byte[4];
	byte[] last_cid = new byte[4];
	byte[] tac = new byte[4];
	byte[] cdr_rel_type = new byte[1];
	
    byte[] last_pcm = new byte[2];
	byte[] identity_request_time  = new byte[4];
	byte[] identity_response_time = new byte[4];
	byte[] ciph_mode_cmd_time = new byte[4];
	byte[] ciph_mode_cmp_time = new byte[4];
	byte[] tmsi_realloc_cmd_time = new byte[4];
	byte[] tmsi_realloc_cmp_time = new byte[4];
	byte[] cc_release_time  = new byte[4];
	byte[] cc_release_cmp_time = new byte[4];
	byte[] clear_cmp_time = new byte[4];
	byte[] sccp_release_time = new byte[4];
	byte[] sccp_release_cmp_time = new byte[4];
	
	public static final int SIZE =  777;
	
	public BSSAPCDR()
	{
		
	}
	
	public  boolean decode(byte[] content) 
	{
		try
		{
			//inStream.read(content,0,731);
			System.arraycopy(content, 0, start_time_s, 0, 4);
			System.arraycopy(content, 4, start_time_ns, 0, 4);
			System.arraycopy(content, 8, end_time_s, 0, 4);
			System.arraycopy(content, 12, end_time_ns , 0, 4);
			System.arraycopy(content, 16, cdr_index, 0, 4);
			System.arraycopy(content, 20, cdr_type, 0, 1);
			System.arraycopy(content, 21, cdr_result, 0, 1);
			System.arraycopy(content, 22, base_cdr_index, 0, 4);
			System.arraycopy(content, 26, base_cdr_type, 0, 1);
			
			System.arraycopy(content, 27, tmsi, 0, 4);
			System.arraycopy(content, 31, new_tmsi, 0, 4);
			System.arraycopy(content, 35, imsi, 0, 8);
			System.arraycopy(content, 43, imei, 0, 8);
			System.arraycopy(content, 51, calling_number, 0, 8);	
			System.arraycopy(content, 59, called_number, 0, 8);
			
			System.arraycopy(content, 67, third_number, 0, 24);
			System.arraycopy(content, 91, mgw_ip, 0, 4);
			System.arraycopy(content, 95, msc_server_ip, 0, 4);
			System.arraycopy(content, 99, bsc_spc, 0, 4);
			System.arraycopy(content, 103, msc_spc, 0, 4);
			System.arraycopy(content, 107, lac, 0, 2);
			
			System.arraycopy(content, 109, ci, 0, 2);
			System.arraycopy(content, 111, last_lac, 0, 2);
			System.arraycopy(content, 113, last_ci, 0, 2);
			System.arraycopy(content, 115, cref_cause, 0, 1);
			System.arraycopy(content, 116, cm_rej_cause, 0, 1);
			System.arraycopy(content, 117, lu_rej_cause, 0, 1);
			
			System.arraycopy(content, 118, assign_failure_cause , 0, 1);
			System.arraycopy(content, 119, rr_cause, 0, 1);
			System.arraycopy(content, 120, cip_rej_cause, 0, 1);
			System.arraycopy(content, 121, disconnect_cause, 0, 1);
			System.arraycopy(content, 122, cc_rel_cause, 0, 1);
			System.arraycopy(content, 123, clear_cause , 0, 1);
			System.arraycopy(content, 124, cp_cause, 0, 1);
			
			System.arraycopy(content, 125, rp_cause, 0, 1);
			System.arraycopy(content, 126, ho_cause, 0, 24);
			System.arraycopy(content, 150, ho_failure_cause, 0, 24);
			System.arraycopy(content, 174, first_paging_time, 0, 4);
			System.arraycopy(content, 178, second_paging_time, 0, 4);
			System.arraycopy(content, 182, third_paging_time, 0, 4);
			System.arraycopy(content, 186, fourth_paging_time, 0, 4);
			System.arraycopy(content, 190, cc_time, 0, 4);
			System.arraycopy(content, 194, assignment_time, 0, 4);
			
			System.arraycopy(content, 198, assignment_cmp_time, 0, 4);
			System.arraycopy(content, 202, setup_time, 0, 4);
			System.arraycopy(content, 206, alert_time, 0, 4);
			System.arraycopy(content, 210, connect_time, 0, 4);
			System.arraycopy(content, 214, disconnect_time, 0, 4);
			System.arraycopy(content, 218, clear_request_time, 0, 4);
			System.arraycopy(content, 222, clear_command_time, 0, 4);
			System.arraycopy(content, 226, rp_data_time, 0, 4);
			
			System.arraycopy(content, 230, rp_ack_time, 0, 4);
			System.arraycopy(content, 234, aut_request_time, 0, 4);
			System.arraycopy(content, 238, aut_response_time, 0, 4);
			System.arraycopy(content, 242, cm_service_accept_time , 0, 4);
			System.arraycopy(content, 246, call_confirm_preceding_time, 0, 4);
			System.arraycopy(content, 250, connect_ack_time, 0, 4);
			System.arraycopy(content, 254, smsc, 0, 24);
			
			System.arraycopy(content, 278, sm_type, 0, 1);
			System.arraycopy(content, 279, sm_data_coding_scheme, 0, 1);
			System.arraycopy(content, 280, sm_length, 0, 2);
			System.arraycopy(content, 282, rp_data_count, 0, 1);
			System.arraycopy(content, 283, handover_count, 0, 1);
			
			System.arraycopy(content, 284, info_trans_capability, 0, 1);
			System.arraycopy(content, 285, speech_version, 0, 1);
			System.arraycopy(content, 286, failed_handover_count, 0, 1);
			System.arraycopy(content, 287, sub_cdr_index_set, 0, 200);
			
			System.arraycopy(content, 487, sub_cdr_starttime_set, 0, 200);
			System.arraycopy(content, 687, call_stop, 0, 1);
			System.arraycopy(content, 688, interbsc_ho_count, 0, 1);
			System.arraycopy(content, 689, pcmts, 0, 2);
			
			System.arraycopy(content, 691, bscid, 0, 8);
			System.arraycopy(content, 699, call_stop_msg, 0, 1);
			System.arraycopy(content, 700, call_stop_cause, 0, 1);
			System.arraycopy(content, 701, mscid, 0, 4);
			System.arraycopy(content, 705, last_bscid, 0, 8);
			
			System.arraycopy(content, 713, last_mscid, 0, 4);
			System.arraycopy(content, 717, dtmf, 0, 1);
			System.arraycopy(content, 718, cid, 0, 4);
			System.arraycopy(content, 722, last_cid, 0, 4);
			System.arraycopy(content, 726, tac, 0, 4);
			System.arraycopy(content, 730, cdr_rel_type, 0, 1);
			
			
			System.arraycopy(content, 731, last_pcm, 0, 2);
			System.arraycopy(content, 733, identity_request_time , 0, 4);
			System.arraycopy(content, 737, identity_response_time, 0, 4);
			System.arraycopy(content, 741, ciph_mode_cmd_time, 0, 4);
			System.arraycopy(content, 745, ciph_mode_cmp_time, 0, 4);
			System.arraycopy(content, 749, tmsi_realloc_cmd_time, 0, 4);
			System.arraycopy(content, 753, tmsi_realloc_cmp_time, 0, 4);
			System.arraycopy(content, 757, cc_release_time , 0, 4);
			System.arraycopy(content, 761, cc_release_cmp_time, 0, 4);
			System.arraycopy(content, 765, clear_cmp_time, 0, 4);
			System.arraycopy(content, 769, sccp_release_time, 0, 4);
			System.arraycopy(content, 773, sccp_release_cmp_time, 0, 4);
			
		 return true;
		}catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}
	
	public  boolean ReadCDRFromHDFS(FSDataInputStream hdfsStream)
	{
	
		try
		{
			byte[] content = new byte[SIZE];
			hdfsStream.read(content,0,SIZE);
			System.arraycopy(content, 0, start_time_s, 0, 4);
			System.arraycopy(content, 4, start_time_ns, 0, 4);
			System.arraycopy(content, 8, end_time_s, 0, 4);
			System.arraycopy(content, 12, end_time_ns , 0, 4);
			System.arraycopy(content, 16, cdr_index, 0, 4);
			System.arraycopy(content, 20, cdr_type, 0, 1);
			System.arraycopy(content, 21, cdr_result, 0, 1);
			System.arraycopy(content, 22, base_cdr_index, 0, 4);
			System.arraycopy(content, 26, base_cdr_type, 0, 1);
			
			System.arraycopy(content, 27, tmsi, 0, 4);
			System.arraycopy(content, 31, new_tmsi, 0, 4);
			System.arraycopy(content, 35, imsi, 0, 8);
			System.arraycopy(content, 43, imei, 0, 8);
			System.arraycopy(content, 51, calling_number, 0, 8);	
			System.arraycopy(content, 59, called_number, 0, 8);
			
			System.arraycopy(content, 67, third_number, 0, 24);
			System.arraycopy(content, 91, mgw_ip, 0, 4);
			System.arraycopy(content, 95, msc_server_ip, 0, 4);
			System.arraycopy(content, 99, bsc_spc, 0, 4);
			System.arraycopy(content, 103, msc_spc, 0, 4);
			System.arraycopy(content, 107, lac, 0, 2);
			
			System.arraycopy(content, 109, ci, 0, 2);
			System.arraycopy(content, 111, last_lac, 0, 2);
			System.arraycopy(content, 113, last_ci, 0, 2);
			System.arraycopy(content, 115, cref_cause, 0, 1);
			System.arraycopy(content, 116, cm_rej_cause, 0, 1);
			System.arraycopy(content, 117, lu_rej_cause, 0, 1);
			
			System.arraycopy(content, 118, assign_failure_cause , 0, 1);
			System.arraycopy(content, 119, rr_cause, 0, 1);
			System.arraycopy(content, 120, cip_rej_cause, 0, 1);
			System.arraycopy(content, 121, disconnect_cause, 0, 1);
			System.arraycopy(content, 122, cc_rel_cause, 0, 1);
			System.arraycopy(content, 123, clear_cause , 0, 1);
			System.arraycopy(content, 124, cp_cause, 0, 1);
			
			System.arraycopy(content, 125, rp_cause, 0, 1);
			System.arraycopy(content, 126, ho_cause, 0, 24);
			System.arraycopy(content, 150, ho_failure_cause, 0, 24);
			System.arraycopy(content, 174, first_paging_time, 0, 4);
			System.arraycopy(content, 178, second_paging_time, 0, 4);
			System.arraycopy(content, 182, third_paging_time, 0, 4);
			System.arraycopy(content, 186, fourth_paging_time, 0, 4);
			System.arraycopy(content, 190, cc_time, 0, 4);
			System.arraycopy(content, 194, assignment_time, 0, 4);
			
			System.arraycopy(content, 198, assignment_cmp_time, 0, 4);
			System.arraycopy(content, 202, setup_time, 0, 4);
			System.arraycopy(content, 206, alert_time, 0, 4);
			System.arraycopy(content, 210, connect_time, 0, 4);
			System.arraycopy(content, 214, disconnect_time, 0, 4);
			System.arraycopy(content, 218, clear_request_time, 0, 4);
			System.arraycopy(content, 222, clear_command_time, 0, 4);
			System.arraycopy(content, 226, rp_data_time, 0, 4);
			
			System.arraycopy(content, 230, rp_ack_time, 0, 4);
			System.arraycopy(content, 234, aut_request_time, 0, 4);
			System.arraycopy(content, 238, aut_response_time, 0, 4);
			System.arraycopy(content, 242, cm_service_accept_time , 0, 4);
			System.arraycopy(content, 246, call_confirm_preceding_time, 0, 4);
			System.arraycopy(content, 250, connect_ack_time, 0, 4);
			System.arraycopy(content, 254, smsc, 0, 24);
			
			System.arraycopy(content, 278, sm_type, 0, 1);
			System.arraycopy(content, 279, sm_data_coding_scheme, 0, 1);
			System.arraycopy(content, 280, sm_length, 0, 2);
			System.arraycopy(content, 282, rp_data_count, 0, 1);
			System.arraycopy(content, 283, handover_count, 0, 1);
			
			System.arraycopy(content, 284, info_trans_capability, 0, 1);
			System.arraycopy(content, 285, speech_version, 0, 1);
			System.arraycopy(content, 286, failed_handover_count, 0, 1);
			System.arraycopy(content, 287, sub_cdr_index_set, 0, 200);
			
			System.arraycopy(content, 487, sub_cdr_starttime_set, 0, 200);
			System.arraycopy(content, 687, call_stop, 0, 1);
			System.arraycopy(content, 688, interbsc_ho_count, 0, 1);
			System.arraycopy(content, 689, pcmts, 0, 2);
			
			System.arraycopy(content, 691, bscid, 0, 8);
			System.arraycopy(content, 699, call_stop_msg, 0, 1);
			System.arraycopy(content, 700, call_stop_cause, 0, 1);
			System.arraycopy(content, 701, mscid, 0, 4);
			System.arraycopy(content, 705, last_bscid, 0, 8);
			
			System.arraycopy(content, 713, last_mscid, 0, 4);
			System.arraycopy(content, 717, dtmf, 0, 1);
			System.arraycopy(content, 718, cid, 0, 4);
			System.arraycopy(content, 722, last_cid, 0, 4);
			System.arraycopy(content, 726, tac, 0, 4);
			System.arraycopy(content, 730, cdr_rel_type, 0, 1);
			
			
			System.arraycopy(content, 731, last_pcm, 0, 2);
			System.arraycopy(content, 733, identity_request_time , 0, 4);
			System.arraycopy(content, 737, identity_response_time, 0, 4);
			System.arraycopy(content, 741, ciph_mode_cmd_time, 0, 4);
			System.arraycopy(content, 745, ciph_mode_cmp_time, 0, 4);
			System.arraycopy(content, 749, tmsi_realloc_cmd_time, 0, 4);
			System.arraycopy(content, 753, tmsi_realloc_cmp_time, 0, 4);
			System.arraycopy(content, 757, cc_release_time , 0, 4);
			System.arraycopy(content, 761, cc_release_cmp_time, 0, 4);
			System.arraycopy(content, 765, clear_cmp_time, 0, 4);
			System.arraycopy(content, 769, sccp_release_time, 0, 4);
			System.arraycopy(content, 773, sccp_release_cmp_time, 0, 4);
			
		
		 return true;
		}catch(Exception e)
		{
			e.printStackTrace();
		return false;
		}
	}
	
	
	public boolean Read( FileInputStream finput)
	{
		try
		{
			byte[] content = new byte[731];
			finput.read(content,0,731);
			System.arraycopy(content, 0, start_time_s, 0, 4);
			System.arraycopy(content, 4, start_time_ns, 0, 4);
			System.arraycopy(content, 8, end_time_s, 0, 4);
			System.arraycopy(content, 12, end_time_ns , 0, 4);
			System.arraycopy(content, 16, cdr_index, 0, 4);
			System.arraycopy(content, 20, cdr_type, 0, 1);
			System.arraycopy(content, 21, cdr_result, 0, 1);
			System.arraycopy(content, 22, base_cdr_index, 0, 4);
			System.arraycopy(content, 26, base_cdr_type, 0, 1);
			
			System.arraycopy(content, 27, tmsi, 0, 4);
			System.arraycopy(content, 31, new_tmsi, 0, 4);
			System.arraycopy(content, 35, imsi, 0, 8);
			System.arraycopy(content, 43, imei, 0, 8);
			System.arraycopy(content, 51, calling_number, 0, 8);	
			System.arraycopy(content, 59, called_number, 0, 8);
			
			System.arraycopy(content, 67, third_number, 0, 24);
			System.arraycopy(content, 91, mgw_ip, 0, 4);
			System.arraycopy(content, 95, msc_server_ip, 0, 4);
			System.arraycopy(content, 99, bsc_spc, 0, 4);
			System.arraycopy(content, 103, msc_spc, 0, 4);
			System.arraycopy(content, 107, lac, 0, 2);
			
			System.arraycopy(content, 109, ci, 0, 2);
			System.arraycopy(content, 111, last_lac, 0, 2);
			System.arraycopy(content, 113, last_ci, 0, 2);
			System.arraycopy(content, 115, cref_cause, 0, 1);
			System.arraycopy(content, 116, cm_rej_cause, 0, 1);
			System.arraycopy(content, 117, lu_rej_cause, 0, 1);
			
			System.arraycopy(content, 118, assign_failure_cause , 0, 1);
			System.arraycopy(content, 119, rr_cause, 0, 1);
			System.arraycopy(content, 120, cip_rej_cause, 0, 1);
			System.arraycopy(content, 121, disconnect_cause, 0, 1);
			System.arraycopy(content, 122, cc_rel_cause, 0, 1);
			System.arraycopy(content, 123, clear_cause , 0, 1);
			System.arraycopy(content, 124, cp_cause, 0, 1);
			
			System.arraycopy(content, 125, rp_cause, 0, 1);
			System.arraycopy(content, 126, ho_cause, 0, 24);
			System.arraycopy(content, 150, ho_failure_cause, 0, 24);
			System.arraycopy(content, 174, first_paging_time, 0, 4);
			System.arraycopy(content, 178, second_paging_time, 0, 4);
			System.arraycopy(content, 182, third_paging_time, 0, 4);
			System.arraycopy(content, 186, fourth_paging_time, 0, 4);
			System.arraycopy(content, 190, cc_time, 0, 4);
			System.arraycopy(content, 194, assignment_time, 0, 4);
			
			System.arraycopy(content, 198, assignment_cmp_time, 0, 4);
			System.arraycopy(content, 202, setup_time, 0, 4);
			System.arraycopy(content, 206, alert_time, 0, 4);
			System.arraycopy(content, 210, connect_time, 0, 4);
			System.arraycopy(content, 214, disconnect_time, 0, 4);
			System.arraycopy(content, 218, clear_request_time, 0, 4);
			System.arraycopy(content, 222, clear_command_time, 0, 4);
			System.arraycopy(content, 226, rp_data_time, 0, 4);
			
			System.arraycopy(content, 230, rp_ack_time, 0, 4);
			System.arraycopy(content, 234, aut_request_time, 0, 4);
			System.arraycopy(content, 238, aut_response_time, 0, 4);
			System.arraycopy(content, 242, cm_service_accept_time , 0, 4);
			System.arraycopy(content, 246, call_confirm_preceding_time, 0, 4);
			System.arraycopy(content, 250, connect_ack_time, 0, 4);
			System.arraycopy(content, 254, smsc, 0, 24);
			
			System.arraycopy(content, 278, sm_type, 0, 1);
			System.arraycopy(content, 279, sm_data_coding_scheme, 0, 1);
			System.arraycopy(content, 280, sm_length, 0, 2);
			System.arraycopy(content, 282, rp_data_count, 0, 1);
			System.arraycopy(content, 283, handover_count, 0, 1);
			
			System.arraycopy(content, 284, info_trans_capability, 0, 1);
			System.arraycopy(content, 285, speech_version, 0, 1);
			System.arraycopy(content, 286, failed_handover_count, 0, 1);
			System.arraycopy(content, 287, sub_cdr_index_set, 0, 200);
			
			System.arraycopy(content, 487, sub_cdr_starttime_set, 0, 200);
			System.arraycopy(content, 687, call_stop, 0, 1);
			System.arraycopy(content, 688, interbsc_ho_count, 0, 1);
			System.arraycopy(content, 689, pcmts, 0, 2);
			
			System.arraycopy(content, 691, bscid, 0, 8);
			System.arraycopy(content, 699, call_stop_msg, 0, 1);
			System.arraycopy(content, 700, call_stop_cause, 0, 1);
			System.arraycopy(content, 701, mscid, 0, 4);
			System.arraycopy(content, 705, last_bscid, 0, 8);
			
			System.arraycopy(content, 713, last_mscid, 0, 4);
			System.arraycopy(content, 717, dtmf, 0, 1);
			System.arraycopy(content, 718, cid, 0, 4);
			System.arraycopy(content, 722, last_cid, 0, 4);
			System.arraycopy(content, 726, tac, 0, 4);
			System.arraycopy(content, 730, cdr_rel_type, 0, 1);
			
			
			System.arraycopy(content, 731, last_pcm, 0, 2);
			System.arraycopy(content, 733, identity_request_time , 0, 4);
			System.arraycopy(content, 737, identity_response_time, 0, 4);
			System.arraycopy(content, 741, ciph_mode_cmd_time, 0, 4);
			System.arraycopy(content, 745, ciph_mode_cmp_time, 0, 4);
			System.arraycopy(content, 749, tmsi_realloc_cmd_time, 0, 4);
			System.arraycopy(content, 753, tmsi_realloc_cmp_time, 0, 4);
			System.arraycopy(content, 757, cc_release_time , 0, 4);
			System.arraycopy(content, 761, cc_release_cmp_time, 0, 4);
			System.arraycopy(content, 765, clear_cmp_time, 0, 4);
			System.arraycopy(content, 769, sccp_release_time, 0, 4);
			System.arraycopy(content, 773, sccp_release_cmp_time, 0, 4);
			
		 return true;
		}catch(Exception e)
		{
			e.printStackTrace();
		return false;
		}
	}
	
	public void Write(FileOutputStream out) 
	{
		if(out != null )
		{
			try
			{
				out.write(start_time_s);
				out.write(start_time_ns);
				out.write(end_time_s);
				out.write(end_time_ns);
				out.write(cdr_index);
				out.write(cdr_type);
				out.write(cdr_result);
				out.write(base_cdr_index);
				out.write(base_cdr_type);
				
				out.write(tmsi);
				out.write(new_tmsi);
				out.write(imsi);
				out.write(imei);
				out.write(calling_number);	
				out.write(called_number);
				
				out.write(third_number);
				out.write(mgw_ip);
				out.write(msc_server_ip);
				out.write(bsc_spc);
				out.write(msc_spc);
				out.write(lac);
				
				out.write(ci);
				out.write(last_lac);
				out.write(last_ci);
				out.write(cref_cause);
				out.write(cm_rej_cause);
				out.write(lu_rej_cause);
				
				out.write(assign_failure_cause);
				out.write(rr_cause);
				out.write(cip_rej_cause);
				out.write(disconnect_cause);
				out.write(cc_rel_cause);
				out.write(clear_cause);
				out.write(cp_cause);
				
				out.write(rp_cause);
				out.write(ho_cause);
				out.write(ho_failure_cause);
				out.write(first_paging_time);
				out.write(second_paging_time);
				out.write(third_paging_time);
				out.write(fourth_paging_time);
				out.write(cc_time);
				out.write(assignment_time);
				
				out.write(assignment_cmp_time);
				out.write(setup_time);
				out.write(alert_time);
				out.write(connect_time);
				out.write(disconnect_time);
				out.write(clear_request_time);
				out.write(clear_command_time);
				out.write(rp_data_time);
				
				out.write(rp_ack_time);
				out.write(aut_request_time);
				out.write(aut_response_time);
				out.write(cm_service_accept_time);
				out.write(call_confirm_preceding_time);
				out.write(connect_ack_time);
				out.write(smsc);
				out.write(sm_type);
				out.write(sm_data_coding_scheme);
				out.write(sm_length);
				out.write(rp_data_count);
				out.write(handover_count);
				
				out.write(info_trans_capability);
				out.write(speech_version);
				out.write(failed_handover_count);
				out.write(sub_cdr_index_set);
				
				out.write(sub_cdr_starttime_set);
				out.write(call_stop);
				out.write(interbsc_ho_count);
				out.write(pcmts);
				
				out.write(bscid);
				out.write(call_stop_msg);
				out.write(call_stop_cause);
				out.write(mscid);
				out.write(last_bscid);
				
				out.write(last_mscid);
				out.write(dtmf);
				out.write(cid);
				out.write(last_cid);
				out.write(tac);
				out.write(cdr_rel_type);
				
				out.write(last_pcm);
				out.write(identity_request_time );
				out.write(identity_response_time);
				out.write(ciph_mode_cmd_time);
				out.write(ciph_mode_cmp_time);
				out.write(tmsi_realloc_cmd_time);
				out.write(tmsi_realloc_cmp_time);
				out.write(cc_release_time );
				out.write(cc_release_cmp_time);
				
				out.write(clear_cmp_time);
				out.write(sccp_release_time);
				out.write(sccp_release_cmp_time);

			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public String getStart_time_s() {
		return BytesToString.Bytes4ToString(start_time_s);
	}

	public String getStart_time_ns() {
		return BytesToString.Bytes4ToString(start_time_ns);
	}


	public String getEnd_time_s() {
		return BytesToString.Bytes4ToString(end_time_s);
	}
	
	/**
	 * adde by Yang Xiao-liang
	 * @return
	 */
	public long getEnd_time_s_long() {
		long endTimeS = 0L;
		for(int i=0; i<end_time_s.length; i++){
			endTimeS <<=8;
			endTimeS |= end_time_s[i]&0xFFL;
		}
		return endTimeS;
	}


	public String getEnd_time_ns() {
		return BytesToString.Bytes4ToString(end_time_ns);
	}

	public String getCdr_index() {
		return BytesToString.Bytes4ToString(cdr_index);
	}

	public String getCdr_type() {
		return BytesToString.Byte1ToString(cdr_type);
	}

	public String getCdr_result() {
		return BytesToString.Byte1ToString(cdr_result);
	}

	public String getBase_cdr_index() {
		return BytesToString.Bytes4ToString(base_cdr_index);
	}

	public String getBase_cdr_type() {
		return BytesToString.Byte1ToString(base_cdr_type);
	}

	public String getTmsi() {
		return BytesToString.Bytes4ToString(tmsi);
	}

	public String getNew_tmsi() {
		return BytesToString.Bytes4ToString(new_tmsi);
	}

	public String getImsi() {
		return BytesToString.Bytes8toString(imsi);
	}

	public String getImei() {
		return BytesToString.Bytes8toString(imei);
	}

	public String getCalling_number() {
		return BytesToString.Bytes8toString(calling_number);
	}

	public String getCalled_number() {
		return BytesToString.Bytes8toString(called_number);
	}

	public String getThird_number() {
		return BytesToString.CharstoString(third_number); // chars
	}

	public String getMgw_ip() {
		return BytesToString.Bytes4ToString(mgw_ip);
	}

	public String getMsc_server_ip() {
		return BytesToString.Bytes4ToString(msc_server_ip);
	}

	public String getBsc_spc() {
		return BytesToString.Bytes4ToString(bsc_spc);
	}

	public String getMsc_spc() {
		return BytesToString.Bytes4ToString(msc_spc);
	}

	public String getLac() {
		return BytesToString.Bytes2ToString(lac);
	}

	public String getCi() {
		return BytesToString.Bytes2ToString(ci);
	}

	public String getLast_lac() {
		return BytesToString.Bytes2ToString(last_lac);
	}

	public String getLast_ci() {
		return BytesToString.Bytes2ToString(last_ci);
	}

	public String getCref_cause() {
		return BytesToString.Byte1ToString(cref_cause);
	}

	public String getCm_rej_cause() {
		return BytesToString.Byte1ToString(cm_rej_cause);
	}

	public String getLu_rej_cause() {
		return BytesToString.Byte1ToString(lu_rej_cause);
	}

	public String getAssign_failure_cause() {
		return BytesToString.Byte1ToString(assign_failure_cause);
	}

	public String getRr_cause() {
		return BytesToString.Byte1ToString(rr_cause);
	}

	public String getCip_rej_cause() {
		return BytesToString.Byte1ToString(cip_rej_cause);
	}

	public String getDisconnect_cause() {
		return BytesToString.Byte1ToString(disconnect_cause);
	}

	public String getCc_rel_cause() {
		return BytesToString.Byte1ToString(cc_rel_cause);
	}

	public String getClear_cause() {
		return BytesToString.Byte1ToString(clear_cause);
	}

	public String getCp_cause() {
		return BytesToString.Byte1ToString(cp_cause);
	}

	public String getRp_cause() {
		return BytesToString.Byte1ToString(rp_cause);
	}

	public String getHo_cause() {
		return BytesToString.CharstoString(ho_cause);// chars
	}

	public String getHo_failure_cause() {
		return BytesToString.CharstoString(ho_failure_cause);// chars
	}

	public String getFirst_paging_time() {
		return BytesToString.Bytes4ToString(first_paging_time);
	}

	public String getSecond_paging_time() {
		return BytesToString.Bytes4ToString(second_paging_time);
	}

	public String getThird_paging_time() {
		return BytesToString.Bytes4ToString(third_paging_time);
	}

	public String getFourth_paging_time() {
		return BytesToString.Bytes4ToString(fourth_paging_time);
	}

	public String getCc_time() {
		return BytesToString.Bytes4ToString(cc_time);
	}

	public String getAssignment_time() {
		return BytesToString.Bytes4ToString(assignment_time);
	}

	public String getAssignment_cmp_time() {
		return BytesToString.Bytes4ToString(assignment_cmp_time);
	}

	public String getSetup_time() {
		return BytesToString.Bytes4ToString(setup_time);
	}

	public String getAlert_time() {
		return BytesToString.Bytes4ToString(alert_time);
	}

	public String getConnect_time() {
		return BytesToString.Bytes4ToString(connect_time);
	}

	public String getDisconnect_time() {
		return BytesToString.Bytes4ToString(disconnect_time);
	}

	public String getClear_request_time() {
		return BytesToString.Bytes4ToString(clear_request_time);
	}

	public String getClear_command_time() {
		return BytesToString.Bytes4ToString(clear_command_time);
	}

	public String getRp_data_time() {
		return BytesToString.Bytes4ToString(rp_data_time);
	}

	public String getRp_ack_time() {
		return BytesToString.Bytes4ToString(rp_ack_time);
	}

	public String getAut_request_time() {
		return BytesToString.Bytes4ToString(aut_request_time);
	}

	public String getAut_response_time() {
		return BytesToString.Bytes4ToString(aut_response_time);
	}

	public String getCm_service_accept_time() {
		return BytesToString.Bytes4ToString(cm_service_accept_time);
	}

	public String getCall_confirm_preceding_time() {
		return BytesToString.Bytes4ToString(call_confirm_preceding_time);
	}

	public String getConnect_ack_time() {
		return BytesToString.Bytes4ToString(connect_ack_time);
	}

	public String getSmsc() {
		return BytesToString.CharstoString(smsc);// chars
	}

	public String getSm_type() {
		return BytesToString.Byte1ToString(sm_type);
	}

	public String getSm_data_coding_scheme() {
		return BytesToString.Byte1ToString(sm_data_coding_scheme);
	}

	public String getSm_length() {
		return BytesToString.Bytes2ToString(sm_length);
	}

	public String getRp_data_count() {
		return BytesToString.Byte1ToString(rp_data_count);
	}

	public String getHandover_count() {
		return BytesToString.Byte1ToString(handover_count);
	}

	public String getInfo_trans_capability() {
		return BytesToString.Byte1ToString(info_trans_capability);
	}

	public String getSpeech_version() {
		return BytesToString.Byte1ToString(speech_version);
	}

	public String getFailed_handover_count() {
		return BytesToString.Byte1ToString(failed_handover_count);
	}

	public String getSub_cdr_index_set() {
		return BytesToString.CharstoString(sub_cdr_index_set);// 200
	}

	public String getSub_cdr_starttime_set() {
		return BytesToString.CharstoString(sub_cdr_starttime_set);// 200
	}

	public String getCall_stop() {
		return BytesToString.Byte1ToString(call_stop);
	}

	public String getInterbsc_ho_count() {
		return BytesToString.Byte1ToString(interbsc_ho_count);
	}

	public String getPcmts() {
		return BytesToString.Bytes2ToString(pcmts);
	}

	public String getBscid() {
		return BytesToString.Bytes8toString(bscid);
	}

	public String getCall_stop_msg() {
		return BytesToString.Byte1ToString(call_stop_msg);
	}

	public String getCall_stop_cause() {
		return BytesToString.Byte1ToString(call_stop_cause);
	}

	public String getMscid() {
		return BytesToString.Bytes4ToString(mscid);
	}

	public String getLast_bscid() {
		return BytesToString.Bytes8toString(last_bscid);
	}

	public String getLast_mscid() {
		return BytesToString.Bytes4ToString(last_mscid);
	}

	public String getDtmf() {
		return BytesToString.Byte1ToString(dtmf);
	}

	public String getCid() {
		return BytesToString.Bytes4ToString(cid);
	}

	public String getLast_cid() {
		return BytesToString.Bytes4ToString(last_cid);
	}

	public String getTac() {
		return BytesToString.Bytes4ToString(tac);
	}

	public String getCdr_rel_type() {
		return BytesToString.Byte1ToString(cdr_rel_type);
	}

	public String getLast_pcm() {
		return BytesToString.Bytes2ToString(last_pcm);
	}
	
	public String getIdentity_request_time () {
		return BytesToString.Bytes4ToString(identity_request_time );
	}
	public String getIdentity_response_time() {
		return BytesToString.Bytes4ToString(identity_response_time);
	}
	public String getCiph_mode_cmd_time() {
		return BytesToString.Bytes4ToString(ciph_mode_cmd_time);
	}
	public String getCiph_mode_cmp_time() {
		return BytesToString.Bytes4ToString(ciph_mode_cmp_time);
	}
	public String getTmsi_realloc_cmd_time() {
		return BytesToString.Bytes4ToString(tmsi_realloc_cmd_time);
	}
	public String getTmsi_realloc_cmp_time() {
		return BytesToString.Bytes4ToString(tmsi_realloc_cmp_time);
	}
	public String getCc_release_time () {
		return BytesToString.Bytes4ToString(cc_release_time );
	}
	public String getCc_release_cmp_time() {
		return BytesToString.Bytes4ToString(cc_release_cmp_time);
	}
	public String getClear_cmp_time() {
		return BytesToString.Bytes4ToString(clear_cmp_time);
	}
	public String getSccp_release_time() {
		return BytesToString.Bytes4ToString(sccp_release_time);
	}
	public String getSccp_release_cmp_time() {
		return BytesToString.Bytes4ToString(sccp_release_cmp_time);
	}

	public void AddWriteList(BlockingQueue queue)
	{
		try
		{
		queue.put(this);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
}
