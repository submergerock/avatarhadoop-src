package org.apache.hadoop.hdfs.job;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class JavaToJson {
	public static List<String> getBSSAPCDRValue(byte[] content)
			throws IOException {

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss");

		BSSAPCDR bssapcdr = new BSSAPCDR();

		List<String> listString = new ArrayList<String>();

		boolean b = bssapcdr.decode(content);

		if (b) {

			// // to_char(to_date('1970-1-1 00:00:00','yyyy-mm-dd
			// hh24:mi:ss')+t1.start_time_s/3600/24+8/24,'yyyy-mm-dd
			// hh24:mi:ss') as begintime,

			// // to_char(to_date('1970-1-1 00:00:00','yyyy-mm-dd
			// hh24:mi:ss')+t1.end_time_s/3600/24+8/24,'yyyy-mm-dd hh24:mi:ss')
			// as endtime

			Date date = new Date();

			date.setTime((Long.valueOf(bssapcdr.getStart_time_s()) * 1000L));

			listString.add(simpleDateFormat.format(date));

			date.setTime((Long.valueOf(bssapcdr.getEnd_time_s()) * 1000L));

			listString.add(simpleDateFormat.format(date));

			listString.add(bssapcdr.getStart_time_s());

			listString.add(bssapcdr.getStart_time_ns());

			listString.add(bssapcdr.getEnd_time_s());

			listString.add(bssapcdr.getEnd_time_ns());

			listString.add(bssapcdr.getCdr_index());

			listString.add(bssapcdr.getCdr_type());

			listString.add(bssapcdr.getCdr_result());

			listString.add(bssapcdr.getBase_cdr_index());

			listString.add(bssapcdr.getBase_cdr_type());

			listString.add(bssapcdr.getTmsi());

			listString.add(bssapcdr.getNew_tmsi());

			listString.add(bssapcdr.getImsi());

			listString.add(bssapcdr.getImei());

			listString.add(bssapcdr.getCalling_number());

			listString.add(bssapcdr.getCalled_number());

			// ----

			listString.add(bssapcdr.getThird_number());

			// ----

			listString.add(bssapcdr.getMgw_ip());

			listString.add(bssapcdr.getMsc_server_ip());

			listString.add(bssapcdr.getBsc_spc());

			listString.add(bssapcdr.getMsc_spc());

			listString.add(bssapcdr.getLac());

			listString.add(bssapcdr.getCi());

			listString.add(bssapcdr.getLast_lac());

			listString.add(bssapcdr.getLast_ci());

			listString.add(bssapcdr.getCref_cause());

			listString.add(bssapcdr.getCm_rej_cause());

			listString.add(bssapcdr.getLu_rej_cause());

			listString.add(bssapcdr.getAssign_failure_cause());

			listString.add(bssapcdr.getRr_cause());

			listString.add(bssapcdr.getCip_rej_cause());

			listString.add(bssapcdr.getDisconnect_cause());

			listString.add(bssapcdr.getCc_rel_cause());

			listString.add(bssapcdr.getClear_cause());

			listString.add(bssapcdr.getCp_cause());

			listString.add(bssapcdr.getRp_cause());

			// ----

			listString.add(bssapcdr.getHo_cause());

			listString.add(bssapcdr.getHo_failure_cause());

			// ----

			listString.add(bssapcdr.getFirst_paging_time());

			listString.add(bssapcdr.getSecond_paging_time());

			listString.add(bssapcdr.getThird_paging_time());

			listString.add(bssapcdr.getFourth_paging_time());

			listString.add(bssapcdr.getCc_time());

			listString.add(bssapcdr.getAssignment_time());

			listString.add(bssapcdr.getAssignment_cmp_time());

			listString.add(bssapcdr.getSetup_time());

			listString.add(bssapcdr.getAlert_time());

			listString.add(bssapcdr.getConnect_time());

			listString.add(bssapcdr.getDisconnect_time());

			listString.add(bssapcdr.getClear_request_time());

			listString.add(bssapcdr.getClear_command_time());

			listString.add(bssapcdr.getRp_data_time());

			listString.add(bssapcdr.getRp_ack_time());

			listString.add(bssapcdr.getAut_request_time());

			listString.add(bssapcdr.getAut_response_time());

			listString.add(bssapcdr.getCm_service_accept_time());

			listString.add(bssapcdr.getCall_confirm_preceding_time());

			listString.add(bssapcdr.getConnect_ack_time());

			// ----

			listString.add(bssapcdr.getSmsc());

			// ----

			listString.add(bssapcdr.getSm_type());

			listString.add(bssapcdr.getSm_data_coding_scheme());

			listString.add(bssapcdr.getSm_length());

			listString.add(bssapcdr.getRp_data_count());

			listString.add(bssapcdr.getHandover_count());

			listString.add(bssapcdr.getInfo_trans_capability());

			listString.add(bssapcdr.getSpeech_version());

			listString.add(bssapcdr.getFailed_handover_count());

			// ----

			listString.add(bssapcdr.getSub_cdr_index_set());

			listString.add(bssapcdr.getSub_cdr_starttime_set());

			// ----

			listString.add(bssapcdr.getCall_stop());

			listString.add(bssapcdr.getInterbsc_ho_count());

			listString.add(bssapcdr.getPcmts());

			listString.add(bssapcdr.getBscid());

			listString.add(bssapcdr.getCall_stop_msg());

			listString.add(bssapcdr.getCall_stop_cause());

			listString.add(bssapcdr.getMscid());

			listString.add(bssapcdr.getLast_bscid());

			listString.add(bssapcdr.getLast_mscid());

			listString.add(bssapcdr.getDtmf());

			listString.add(bssapcdr.getCid());

			listString.add(bssapcdr.getLast_cid());

			listString.add(bssapcdr.getTac());

		}
		return listString;
	}
}
