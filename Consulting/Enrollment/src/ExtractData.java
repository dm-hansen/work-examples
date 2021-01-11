import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.GregorianCalendar;
import java.util.Scanner;
import java.util.regex.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;

/**
 * Class to encapsulate extracting data from PeopleSoft database and
 * augmenting data with data from the US Census ACS data based on
 * zipcode
 *
 * @author David M. Hansen
 */
public class ExtractData 
{

   // DBMS and Database-specific connection parameters
   private static final String JDBC_DRIVER = "oracle.jdbc.OracleDriver";
   private static final String DB_URL = "jdbc:oracle:thin:...";
   private static final String USERNAME = "BOGUS";
   private static final String PASSWORD = "MORE BOGUS";

   // This might change depending on what the census folks offer.
   private static final int MIN_CENSUS_YEAR = 2011; // Earliest year for ACS estimates 
   private static int CENSUS_YEAR; // Will find latest year available
   private static final String CENSUS_CACHE_DIR = "CensusCache/";
   private static final String CENSUS_URL = "http://api.census.gov/data/";
   private static final String CENSUS_DATASET = "acs5"; // 5-year community survey
   private static final String CENSUS_KEY = "GET YOUR OWN KEY";

   private static final double SAT_MAX_SCORE = 800.0;
   private static final double ACT_MAX_SCORE = 36.0;

   // Here we define collections of fields that can be present in the
   // output file depending on switches given. Note that the collections 
   // build on one another; e.g., "apply" includes all of "fall" and adds
   // more fields
   private static final String[] FALL_FIELDS = {
      "GFU_IPEDS_ENROLL_PCT", "GFU_IPEDS_PCT", "ADM_REFRL_SRCE_ENROLL_PCT", 
      "ADM_REFRL_SRCE_PCT", "LAST_SCHOOL_ENROLL_PCT", "LAST_SCHOOL_PCT",
      "REFERRAL_SRCE_DT", "SEX", "STATE_ENROLL_PCT", "STATE_PCT", "ZIP",
      "ZIP_ENROLL_PCT", "ZIP_PCT", "BRUIN_PREVIEW", "OTHER_VISITS", "VISITS"
   };
   // Important that ACT fields come BEFORE SAT fields as they'll pass
   // data to the SAT fields
   private static final String[] APPLY_FIELDS = {
      "ADM_CREATION_DT", "CLASS_STANDING", 
      "GPA", "RELIGIOUS_PREF_ENROLL_PCT", "RELIGIOUS_PREF_PCT",
      "SUBJECT1_ENROLL_PCT", "SUBJECT1_PCT", "SUBJECT2_ENROLL_PCT", "SUBJECT2_PCT",
      "ADM_APPL_METHOD","ACT_READ", "ACT_MATH", "SAT_MATH", "SAT_READ", 
      "AGI", "CASH_SAVINGS", "PARENTS_MARRIED",
      "SCHOLARSHIP_COMP", "SCHOOL1_ENROLL_PCT", "SCHOOL1_PCT",
      "SCHOOL2_ENROLL_PCT", "SCHOOL2_PCT", "SCHOOL3_ENROLL_PCT",
      "SCHOOL3_PCT", "SCHOOL4_ENROLL_PCT", "SCHOOL4_PCT", "GFU_SCHOOL_RANK"
   };
   private static final String[] ADMIT_FIELDS = { "DEPOSIT_DT" };
   // This defines fields that are being predicted
   private static final String[] PREDICT_FIELDS = { "ACAD_PROG_STATUS" };


   private static StringBuilder QUERY = new StringBuilder(" SELECT DISTINCT A.EMPLID, O.LAST_NAME, O.FIRST_NAME, O.MIDDLE_NAME, O.ADDRESS1, O.CITY, O.STATE, P.SEX,  A.RECRUITING_STATUS, TO_CHAR(A.REFERRAL_SRCE_DT,'YYYY-MM-DD') as REFERRAL_SRCE_DT, TO_CHAR(G.FULLY_ENRL_DT,'YYYY-MM-DD') as FULLY_ENRL_DT,  TO_CHAR(B.ADM_CREATION_DT,'YYYY-MM-DD') as ADM_CREATION_DT, TO_CHAR(D.ACTION_DT,'YYYY-MM-DD') as APPL_ACTION_DT,  TO_CHAR(B.ADM_APPL_CMPLT_DT,'YYYY-MM-DD') as APPL_CMPLT_DT, B.ADM_APPL_METHOD, TO_CHAR(F.SRVC_IND_ACTIVE_DT,'YYYY-MM-DD') as DEPOSIT_DT, TO_CHAR(F.SCC_SI_END_DT,'YYYY-MM-DD') as SCC_SI_END_DT, H.PROG_STATUS as ACAD_PROG_STATUS,  gfu_school_rank, gpa.gpa, gpa.pct as CLASS_STANDING, state_total.num / total.num as STATE_PCT, state_enrolled.num / (state_total.num+.01) as STATE_ENROLL_PCT, O.POSTAL as ZIP, total_zip.num / total.num as ZIP_PCT, zip_enrolled.num / (0.1+total_zip.num) as ZIP_ENROLL_PCT, S.GFU_IPEDS_VALUE, total_ethnic.num / (0.1+total_w_ethnic.num) as GFU_IPEDS_PCT, ethnic_enrolled.num / (0.1 + total_ethnic.num) as GFU_IPEDS_ENROLL_PCT, L.RELIGIOUS_PREF, total_relig_pref.num / (0.1+total_w_relig.num) as RELIGIOUS_PREF_PCT, relig_pref_enrolled.num / (0.1+total_relig_pref.num) as RELIGIOUS_PREF_ENROLL_PCT, A.ADM_REFRL_SRCE, total_adm_refrl_srce.num / total.num as ADM_REFRL_SRCE_PCT, adm_refrl_srce_enrolled.num / (0.1+total_adm_refrl_srce.num) as ADM_REFRL_SRCE_ENROLL_PCT, A.last_sch_attend, total_last_school.num / total.num as LAST_SCHOOL_PCT, last_school_enrolled.num / (0.1+total_last_school.num) as LAST_SCHOOL_ENROLL_PCT, intr.ext_subject_area as subject1, total_interest1.num / (0.1+total_w_interest1.num) as SUBJECT1_PCT, interest1_enrolled.num / (0.1+total_interest1.num) as SUBJECT1_ENROLL_PCT, intr2.ext_subject_area as subject2, total_interest2.num / (0.1+total_w_interest2.num) as SUBJECT2_PCT, interest2_enrolled.num / (0.1+total_interest2.num) as SUBJECT2_ENROLL_PCT, genesis, visits , scholarship_comp, bruin_preview, other_visits, act_read , act_comp, act_engl, act_scire, act_math, sat_read, sat_write, sat_math, pfafsa.agi, pfafsa.cash_savings, total_school1.num / total_w_fafsa.num as SCHOOL1_PCT, school1_enrolled.num / (0.1+total_school1.num) as SCHOOL1_ENROLL_PCT, total_school2.num / total_w_fafsa.num as SCHOOL2_PCT, school2_enrolled.num / (0.1+total_school2.num) as SCHOOL2_ENROLL_PCT, total_school3.num / total_w_fafsa.num as SCHOOL3_PCT, school3_enrolled.num / (0.1+total_school3.num) as SCHOOL3_ENROLL_PCT, total_school4.num / total_w_fafsa.num as SCHOOL4_PCT, school4_enrolled.num / (0.1+total_school4.num) as SCHOOL4_ENROLL_PCT, pfafsa.marital_stat as PARENTS_MARRIED FROM  sysadm.PS_ADM_PRSPCT_CAR A LEFT OUTER JOIN sysadm.PS_GFU_ADM_APPL_VW  B ON A.EMPLID = B.EMPLID AND A.ACAD_CAREER = B.ACAD_CAREER AND A.INSTITUTION = B.INSTITUTION AND A.ADMIT_TERM = B.ADMIT_TERM AND B.PROG_ACTION = 'APPL' left outer join sysadm.PS_GFU_ADM_APPL_VW  D ON  a.EMPLID = D.EMPLID AND a.ACAD_CAREER = D.ACAD_CAREER AND a.INSTITUTION = D.INSTITUTION AND a.ADMIT_TERM = D.ADMIT_TERM AND D.PROG_ACTION IN ('ADMT','COND','DEFR') left outer join sysadm.PS_SRVC_IND_DATA F ON  a.EMPLID = F.EMPLID AND a.INSTITUTION = F.INSTITUTION AND F.SRVC_IND_CD = 'ATD' left outer join sysadm.PS_ACAD_CALTRM_TBL G on A.ADMIT_TERM = G.STRM and A.ACAD_CAREER = G.ACAD_CAREER and A.INSTITUTION = G.INSTITUTION left outer join sysadm.PS_ACAD_PROG H ON  a.EMPLID = H.EMPLID AND a.ACAD_CAREER = H.ACAD_CAREER AND a.INSTITUTION = H.INSTITUTION AND H.PROG_STATUS = 'AC' AND a.ADMIT_TERM = H.ADMIT_TERM left outer join sysadm.PS_RELIGIOUS_PREF L ON  a.EMPLID = L.EMPLID left outer join  sysadm.PS_PERS_DATA_SA_VW P on a.emplid = p.emplid left outer join sysadm.PS_GFU_CC_ETHNC_VW S  on a.emplid = s.emplid left outer join (select LAST_NAME, FIRST_NAME, MIDDLE_NAME, ADDRESS1, CITY, STATE, emplid, name_type, address_type, substr(postal, 1, 5) as postal from sysadm.PS_GFU_ADMCNTCT_VW where REGEXP_LIKE(postal, '^[0-9].*') union select LAST_NAME, FIRST_NAME, MIDDLE_NAME, ADDRESS1, CITY, STATE, emplid, name_type, address_type, REGEXP_REPLACE(postal, ' ', '') as postal from sysadm.PS_GFU_ADMCNTCT_VW where REGEXP_LIKE(postal, '^[^0-9].*')) O on a.emplid = o.emplid AND O.ADDRESS_TYPE = 'HOME' AND O.NAME_TYPE = 'PRF' left outer join (select emplid, max(convert_gpa) as gpa, max(percentile) as pct, ext_org_id from sysadm.PS_EXT_ACAD_SUM x where x.ext_career = 'HS' group by emplid, ext_org_id) gpa on gpa.emplid = a.emplid and gpa.ext_org_id = a.last_sch_attend left outer join sysadm.PS_ADM_INTERESTS intr on a.emplid = intr.emplid and intr.priority=1 left outer join sysadm.PS_ADM_INTERESTS intr2 on a.emplid = intr2.emplid and intr2.priority=2 left outer join sysadm.ps_isir_parent pfafsa on a.emplid = pfafsa.emplid  left outer join sysadm.ps_isir_student fafsa on a.emplid = fafsa.emplid left outer join (select count(*) as visits, emplid from sysadm.ps_campus_evnt_att visit join sysadm.ps_campus_event e1 on e1.campus_event_nbr = visit.campus_event_nbr and e1.campus_event_type in ('FAF','MAF','SMRV','ATHV','VIND') group by emplid) xv on a.emplid = xv.emplid left outer join (select count(*) as genesis, emplid from sysadm.ps_campus_evnt_att visit join sysadm.ps_campus_event e1 on e1.campus_event_nbr = visit.campus_event_nbr and e1.campus_event_type = 'GN' group by emplid) xg on a.emplid = xg.emplid left outer join (select count(*) as scholarship_comp, emplid from sysadm.ps_campus_evnt_att visit join sysadm.ps_campus_event e1 on e1.campus_event_nbr = visit.campus_event_nbr and e1.campus_event_type = 'SC' group by emplid) xs on a.emplid = xs.emplid left outer join (select count(*) as bruin_preview, emplid from sysadm.ps_campus_evnt_att visit join sysadm.ps_campus_event e1 on e1.campus_event_nbr = visit.campus_event_nbr and e1.campus_event_type = 'BP' group by emplid) xb on a.emplid = xb.emplid left outer join (select count(*) as other_visits, emplid from sysadm.ps_campus_evnt_att visit join sysadm.ps_campus_event e1 on e1.campus_event_nbr = visit.campus_event_nbr and e1.campus_event_type not in ('BP','GN','SC','FAF','MAF','SMRV','ATHV','VIND') group by emplid) xx on a.emplid = xx.emplid left outer join (select max(score) as act_read, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'ACT' and test_component = 'READ' group by emplid) xs1 on a.emplid = xs1.emplid left outer join (select max(score) as act_comp, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'ACT' and test_component = 'COMP' group by emplid) xs2 on a.emplid = xs2.emplid left outer join (select max(score) as act_engl, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'ACT' and test_component = 'ENGL' group by emplid) xs3 on a.emplid = xs3.emplid left outer join (select max(score) as act_math, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'ACT' and test_component = 'MATH' group by emplid) xs4 on a.emplid = xs4.emplid left outer join (select max(score) as act_scire, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'ACT' and test_component = 'SCIRE' group by emplid) xs5 on a.emplid = xs5.emplid left outer join (select max(score) as sat_read, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'SATI' and test_component = 'READ' group by emplid) xs6 on a.emplid = xs6.emplid left outer join (select max(score) as sat_math, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'SATI' and test_component = 'MATH' group by emplid) xs7 on a.emplid = xs7.emplid left outer join (select max(score) as sat_write, emplid from sysadm.PS_STDNT_TEST_COMP where test_id = 'SATI' and test_component = 'WR' group by emplid) xs8 on a.emplid = xs8.emplid left outer join (select state, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_GFU_ADMCNTCT_VW Ox on ax.emplid = Ox.emplid AND Ox.ADDRESS_TYPE = 'HOME' AND Ox.NAME_TYPE = 'PRF' where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by state) state_total on state_total.state = o.state left outer join (select state, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.PS_GFU_ADMCNTCT_VW Ox on ax.emplid = Ox.emplid AND Ox.ADDRESS_TYPE = 'HOME' AND Ox.NAME_TYPE = 'PRF' where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by state) state_enrolled on state_enrolled.state=o.state left outer join (select Ox.POSTAL as zip, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join (select emplid, name_type, address_type, substr(postal, 1, 5) as postal from sysadm.PS_GFU_ADMCNTCT_VW where REGEXP_LIKE(postal, '^[0-9].*') union select emplid, name_type, address_type, REGEXP_REPLACE(postal, ' ', '') as postal from sysadm.PS_GFU_ADMCNTCT_VW where REGEXP_LIKE(postal, '^[A-Z].*')) Ox on ax.emplid = Ox.emplid AND Ox.ADDRESS_TYPE = 'HOME' AND Ox.NAME_TYPE = 'PRF' where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.POSTAL) total_zip on total_zip.zip=substr(O.postal,1,5) left outer join (select Ox.POSTAL as zip, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join (select emplid, name_type, address_type, substr(postal, 1, 5) as postal from sysadm.PS_GFU_ADMCNTCT_VW where REGEXP_LIKE(postal, '^[0-9].*') union select emplid, name_type, address_type, REGEXP_REPLACE(postal, ' ', '') as postal from sysadm.PS_GFU_ADMCNTCT_VW where REGEXP_LIKE(postal, '^[A-Z].*')) Ox on ax.emplid = Ox.emplid AND Ox.ADDRESS_TYPE = 'HOME' AND Ox.NAME_TYPE = 'PRF' where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.POSTAL) zip_enrolled on zip_enrolled.zip=O.postal left outer join (select Ox.GFU_IPEDS_VALUE, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_GFU_CC_ETHNC_VW Ox on ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.GFU_IPEDS_VALUE) total_ethnic on total_ethnic.GFU_IPEDS_VALUE=S.GFU_IPEDS_VALUE left outer join (select  Ox.GFU_IPEDS_VALUE, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.PS_GFU_CC_ETHNC_VW Ox on ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.GFU_IPEDS_VALUE) ethnic_enrolled on ethnic_enrolled.GFU_IPEDS_VALUE=S.GFU_IPEDS_VALUE left outer join (select Ox.RELIGIOUS_PREF, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_RELIGIOUS_PREF Ox on ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.RELIGIOUS_PREF) total_relig_pref on total_relig_pref.RELIGIOUS_PREF=L.RELIGIOUS_PREF left outer join (select Ox.RELIGIOUS_PREF, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.PS_RELIGIOUS_PREF Ox on ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.RELIGIOUS_PREF) relig_pref_enrolled on relig_pref_enrolled.RELIGIOUS_PREF=L.RELIGIOUS_PREF left outer join (select Ax.ADM_REFRL_SRCE, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ax.ADM_REFRL_SRCE) total_adm_refrl_srce on total_adm_refrl_srce.ADM_REFRL_SRCE=A.ADM_REFRL_SRCE left outer join (select  Ax.ADM_REFRL_SRCE, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ax.ADM_REFRL_SRCE) adm_refrl_srce_enrolled on adm_refrl_srce_enrolled.ADM_REFRL_SRCE=A.ADM_REFRL_SRCE left outer join (select Ax.last_sch_attend, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ax.last_sch_attend) total_last_school on total_last_school.last_sch_attend=A.last_sch_attend left outer join (select Ax.last_sch_attend, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ax.last_sch_attend) last_school_enrolled on last_school_enrolled.last_sch_attend=A.last_sch_attend left outer join (select Ox.ext_subject_area, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ADM_INTERESTS Ox on ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' and Ox.priority=1 group by Ox.ext_subject_area) total_interest1 on total_interest1.ext_subject_area=intr.ext_subject_area left outer join (select Ox.ext_subject_area, count(distinct ax.emplid) num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.PS_ADM_INTERESTS Ox on Ox.priority=1 and ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.ext_subject_area) interest1_enrolled on interest1_enrolled.ext_subject_area=intr.ext_subject_area left outer join (select Ox.ext_subject_area, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ADM_INTERESTS Ox on ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' and Ox.priority=2 group by Ox.ext_subject_area) total_interest2 on total_interest2.ext_subject_area=intr.ext_subject_area left outer join (select Ox.ext_subject_area, count(distinct ax.emplid) num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.PS_ADM_INTERESTS Ox on Ox.priority=2 and ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.ext_subject_area) interest2_enrolled on interest2_enrolled.ext_subject_area=intr.ext_subject_area left outer join (select Ox.school_choice_1, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_1) total_school1 on total_school1.school_choice_1=fafsa.school_choice_1 left outer join (select Ox.school_choice_1,count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_1) school1_enrolled on school1_enrolled.school_choice_1=fafsa.school_choice_1 left outer join (select Ox.school_choice_2, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_2) total_school2 on total_school2.school_choice_2=fafsa.school_choice_2 left outer join (select Ox.school_choice_2,count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_2) school2_enrolled on school2_enrolled.school_choice_2=fafsa.school_choice_2 left outer join (select Ox.school_choice_3, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_3) total_school3 on total_school3.school_choice_3=fafsa.school_choice_3 left outer join (select Ox.school_choice_3,count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_3) school3_enrolled on school3_enrolled.school_choice_3=fafsa.school_choice_3 left outer join (select Ox.school_choice_4, count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_4) total_school4 on total_school4.school_choice_4=fafsa.school_choice_4 left outer join (select Ox.school_choice_4,count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.PS_ACAD_PROG Hx on ax.EMPLID = Hx.EMPLID AND ax.ACAD_CAREER = Hx.ACAD_CAREER AND ax.INSTITUTION = Hx.INSTITUTION AND Hx.PROG_STATUS = 'AC' AND ax.ADMIT_TERM = Hx.ADMIT_TERM  join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' group by Ox.school_choice_4) school4_enrolled on school4_enrolled.school_choice_4=fafsa.school_choice_4   left outer join (select emplid, max(num) as gfu_school_rank from (select Ox.emplid, 1.0  as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' and Ox.school_choice_1 = '003194' union select Ox.emplid, 0.5  as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' and Ox.school_choice_2 = '003194' union select Ox.emplid, 0.25  as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' and Ox.school_choice_3 = '003194' union select Ox.emplid, 0.125  as num from sysadm.PS_ADM_PRSPCT_CAR Ax join sysadm.ps_isir_student Ox on  ax.emplid = Ox.emplid where Ax.ADMIT_TYPE = 'FYR' AND Ax.ADM_RECR_CTR = 'TUGD' and Ox.school_choice_4 = '003194') group by emplid) gfu_rank on gfu_rank.emplid = a.emplid,(select count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR ax where ax.ADMIT_TYPE = 'FYR' AND ax.ADM_RECR_CTR = 'TUGD') total ,(select count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR ax join sysadm.PS_GFU_CC_ETHNC_VW Ox on ax.emplid = Ox.emplid where ax.ADMIT_TYPE = 'FYR' AND ax.ADM_RECR_CTR = 'TUGD') total_w_ethnic ,(select count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR ax join sysadm.PS_RELIGIOUS_PREF Ox on ax.emplid = Ox.emplid where ax.ADMIT_TYPE = 'FYR' AND ax.ADM_RECR_CTR = 'TUGD') total_w_relig ,(select count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR ax join sysadm.PS_ADM_INTERESTS Ox on Ox.priority=1 and ax.emplid = Ox.emplid where ax.ADMIT_TYPE = 'FYR' AND ax.ADM_RECR_CTR = 'TUGD') total_w_interest1 ,(select count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR ax join sysadm.PS_ADM_INTERESTS Ox on Ox.priority=2 and ax.emplid = Ox.emplid where ax.ADMIT_TYPE = 'FYR' AND ax.ADM_RECR_CTR = 'TUGD') total_w_interest2 ,(select count(distinct ax.emplid) as num from sysadm.PS_ADM_PRSPCT_CAR ax join sysadm.ps_isir_student Ox on ax.emplid = Ox.emplid where ax.ADMIT_TYPE = 'FYR' AND ax.ADM_RECR_CTR = 'TUGD') total_w_fafsa WHERE A.ADMIT_TYPE = 'FYR' AND A.ADM_RECR_CTR = 'TUGD' and (pfafsa.emplid is null or (not exists (select * from sysadm.ps_isir_parent x where x.emplid = a.emplid and x.aid_year < pfafsa.aid_year) and not exists (select * from sysadm.ps_isir_parent x where x.emplid = a.emplid and x.aid_year = pfafsa.aid_year and (x.effdt > pfafsa.effdt or (x.effdt = pfafsa.effdt and x.effseq > pfafsa.effseq))))) and (fafsa.emplid is null or (not exists (select * from sysadm.ps_isir_student x where x.emplid = a.emplid and x.aid_year < fafsa.aid_year) and not exists (select * from sysadm.ps_isir_student x where x.emplid = a.emplid and x.aid_year = fafsa.aid_year and (x.effdt > fafsa.effdt or (x.effdt = fafsa.effdt and x.effseq > fafsa.effseq))))) and (intr.emplid is null or intr.effdt = (select max(effdt) from sysadm.ps_adm_interests x where x.emplid = a.emplid and x.priority=1)) and (intr2.emplid is null or intr2.effdt = (select max(effdt) from sysadm.ps_adm_interests x where x.emplid = a.emplid and x.priority=2)) "); 
   // A cache of zipcode data we've downloaded to avoid re-downloading
   // data. We also keep a cache of median income to replace AGI if the
   // student hasn't filed a FAFSA yet
   static HashMap<String, ArrayList<Double> > ZIP_CACHE = new HashMap<String, ArrayList<Double> >();
   static HashMap<String, Double > ZIP_INCOME_CACHE = new HashMap<String, Double >();



   interface ComputeDataValue 
   {
      /**
       * Computes a data value and adds data to the dataVector for a
       * particular type and instance of data
       *
       * @param dataVector is accumulating vector of data
       * @param dataValue is the name of the data value being computed
       * @param missing_value is value to use when data is missing
       * @param fieldMapping is optional data used in the computation
       * @param fullyEnrolledDate is used for date offset computations
       *
       * @return dataVector with added data
       */
      public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                       String field, 
                                       String dataValue, 
                                       Object missing_value, 
                                       Object fieldMapping,
                                       String fullyEnrolledDate);
   }

   
   private static HashMap<String, Object[]> fieldMap = new HashMap<String, Object[]> () 
   {
      {
         // Template for all numeric data; second parameter is a value
         // to divide the database value by - more generally it will be
         // 1
         put("VISITS", new Object[] {
            new Double(3.0),  // Max out at 3 visits
            new Double(0.0),  // 0 if missing
            new ComputeDataValue() 
            {
               public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                                String field, 
                                                String dataValue, 
                                                Object missing_value,
                                                Object fieldMapping,
                                                String fullyEnrolledDate) 
               {
                  // If we have data, normalize it (max value of 1.0).
                  try
                  {
                     dataVector.add(
                         (dataValue != null) ?
                         Math.min(1.0, (Double.parseDouble(dataValue) / ( (Double) fieldMapping).doubleValue())) : 
                         (Double) missing_value);
                  }
                  catch (NumberFormatException e)
                  {
//System.err.println(e.toString()+" "+field+" : "+dataValue);
                     dataVector.add( (Double) missing_value);
                  }
                  return dataVector;
               };
            }
         }
         );

         // Template for categorical data. Second parameter lists
         // categories that data is mapped onto. 
         put("SEX", new Object[] {
            new String[] {"M","F"},
            new Double(0.0), 
            new ComputeDataValue() 
            {
               public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                                String field, 
                                                String dataValue, 
                                                Object missing_value,
                                                Object fieldMapping,
                                                String fullyEnrolledDate) 
               {
                  // Iterate over the potential values and put a 1 or 0 in
                  // the result vector for each potential value depending on which 
                  // value was actually present (e.g., for state there would be 
                  // 50 potential values yielding 50 different numbers - only one 
                  // of which will be 1.0)
                  for (String key : (String[]) fieldMapping)
                  {
                     // If the key is equal to the data, then mark as present,
                     // otherwise it's not present
                     dataVector.add(key.equals(dataValue) ? 1.0 : (Double) missing_value);
                  }
                  return dataVector;
               }
            }
         }
         );

         // Template for all date values. We compute # days between the
         // database date and the "fullyEnrolledDate"; second parameter is the 
         // number of years to divide by (i.e., here we normalize to a #
         // days between 0 and 365*3)
         put("REFERRAL_SRCE_DT", new Object[] {
            new Double(3.0), // Assume they could be referred to us up to 3 years ago
            new Double(2.0), // If missing assume 2 years
            new ComputeDataValue() 
            {
               public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                                String field, 
                                                String dataValue, 
                                                Object missing_value,
                                                Object fieldMapping,
                                                String fullyEnrolledDate) 
               {
                  if (dataValue == null || fullyEnrolledDate == null) 
                  {
//System.err.println("NULL value for "+field+" : "+dataValue);
                     dataVector.add( (Double) missing_value);
                  }
                  else
                  {
                     try
                     {
                        // Compute the difference between the date and the
                        // fullyEnrolledDate in days and normalize assuming a
                        // maximum value of 365 days * # years given as
                        // 'fieldMapping'
                        GregorianCalendar aDate = 
                           new GregorianCalendar(Integer.parseInt(dataValue.split("-")[0]),
                                                 Integer.parseInt(dataValue.split("-")[1]),
                                                 Integer.parseInt(dataValue.split("-")[2]));
                        GregorianCalendar enrollDate = 
                           new GregorianCalendar(Integer.parseInt(fullyEnrolledDate.split("-")[0]),
                                                 Integer.parseInt(fullyEnrolledDate.split("-")[1]),
                                                 Integer.parseInt(fullyEnrolledDate.split("-")[2]));
                        dataVector.add(
                               Math.min(1.0,
                                       ((enrollDate.getTimeInMillis() - aDate.getTimeInMillis()) / 
                                         (1000 * 60 * 60 * 24) ) / 
                                       (365.0 * ((Double) fieldMapping).doubleValue())));
                     }
                     catch (NumberFormatException e)
                     {
//System.err.println(e.toString()+" DATE :  "+dataValue);
                        dataVector.add( (Double) missing_value);
                     }
                  }
                  return dataVector;
               }
            }
         }
         );

         put("APPL_CMPLT_DT", new Object[] {
            new Double(2.0), // Assume 2 years difference
            new Double(0.0),
            // Same as "referral_srce_dt"
            (ComputeDataValue) (this.get("REFERRAL_SRCE_DT")[2])
         }
         );

         put("ADM_CREATION_DT", new Object[] {
            new Double(2.0), // Assume 2 years difference
            new Double(0.0),
            // Same as "referral_srce_dt"
            (ComputeDataValue) (this.get("REFERRAL_SRCE_DT")[2])
         }
         );

         put("ADM_REFRL_SRCE_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("ADM_REFRL_SRCE_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("STATE_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("STATE_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("RELIGIOUS_PREF_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("RELIGIOUS_PREF_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("GFU_IPEDS_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("GFU_IPEDS_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("ADM_APPL_METHOD", new Object[] {
            new String[] {"WWW"},
            new Double(0.0), // I.e., 0 if using something other than WWW to apply
            // Same as "sex"
            (ComputeDataValue) (this.get("SEX")[2])
         }
         );

         put("BRUIN_PREVIEW", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("OTHER_VISITS", new Object[] {
            new Double(3.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("DEPOSIT_DT", new Object[] {
            new Double(1.0), // Assume 1 year difference
            new Double(0.0),
            // Same as "referral_srce_dt"
            (ComputeDataValue) (this.get("REFERRAL_SRCE_DT")[2])
         }
         );

         put("SCC_SI_END_DT", new Object[] {
            new Double(1.0), // Assume 1 year difference
            new Double(0.0),
            // Same as "referral_srce_dt"
            (ComputeDataValue) (this.get("REFERRAL_SRCE_DT")[2])
         }
         );

         put("LAST_SCHOOL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("LAST_SCHOOL_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("GPA", new Object[] {
            new Double(4.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("CLASS_STANDING", new Object[] {
            new Double(100.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("ZIP_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("ZIP_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SUBJECT1_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SUBJECT1_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SUBJECT2_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SUBJECT2_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("GENESIS", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOLARSHIP_COMP", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("BRUIN_PREVIEW", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SAT_READ", new Object[] {
            null, 
            new Double(0.0),
            // Special method. If we have a value then we store that. If that's 
            // missing but we have a "missing value" (set by ACT_READ!) then 
            // that will be stored. Otherwise we use the "fieldMapping"
            // value which is the average SAT score as set by the
            // setSATDefaults method
            new ComputeDataValue() 
            {
               public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                                String field, 
                                                String dataValue, 
                                                Object missing_value,
                                                Object fieldMapping,
                                                String fullyEnrolledDate) 
               {
                  Double score = null;
                  // If we have data, normalize it (max value of 1.0).
                  try
                  {
                     if (dataValue != null)
                     {
                        score = new Double(
                              Math.min(1.0, (Double.parseDouble(dataValue) / SAT_MAX_SCORE))
                           );
                     }
                     else if (missing_value != null) // we had an ACT score

                     {
                        score = (Double) missing_value;
                     }
                     else // Use the average computed by setDefaultSAT
                     {
                        score = (Double) fieldMapping;
                     }
                  }
                  catch (NumberFormatException e)
                  {
//System.err.println(e.toString()+" "+field+" : "+dataValue);
                  }

                  dataVector.add(score);
                  return dataVector;
               };
            }
         }
         );

         put("SAT_MATH", new Object[] {
            // Same as SAT_READ above
            (Double) (this.get("SAT_READ")[0]),
            (Double) (this.get("SAT_READ")[1]),
            (ComputeDataValue) (this.get("SAT_READ")[2])
         }
         );

/***
 * Only using Reading and Math for SAT and ACT
 *
         put("SAT_WRITE", new Object[] {
            // Same as SAT_READ above
            (Double) (this.get("SAT_READ")[0]),
            (Double) (this.get("SAT_READ")[1]),
            (ComputeDataValue) (this.get("SAT_READ")[2])
         }
         );
***/

         put("ACT_READ", new Object[] {
            new Double(36.0), 
            new Double(0.0),
            // If we have an ACT_READ score, set it as the default for
            // SAT_READ, otherwise set that to NULL; SAT_READ will
            // either overwrite it with a SAT score, or use it as
            // the test score
            new ComputeDataValue() 
            {
               public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                                String field, 
                                                String dataValue, 
                                                Object missing_value,
                                                Object fieldMapping,
                                                String fullyEnrolledDate) 
               {
                  Double score = null;
                  // If we have data, normalize it (max value of 1.0).
                  try
                  {
                     if (dataValue != null)
                     {
                        score = new Double(
                              Math.min(1.0, (Double.parseDouble(dataValue) / ACT_MAX_SCORE))
                           );
                     }
                  }
                  catch (NumberFormatException e)
                  {
System.err.println(e.toString()+" "+field+" : "+dataValue);
                  }

                  fieldMap.get("SAT_READ")[1] = score;
                  // Added nothing to the dataVector
                  return dataVector;
               };
            }
         }
         );


         put("ACT_MATH", new Object[] {
            // Same idea as "ACT_READ"
            (Double) (this.get("ACT_READ")[0]),
            (Double) (this.get("ACT_READ")[1]),
            new ComputeDataValue() 
            {
               public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                                String field, 
                                                String dataValue, 
                                                Object missing_value,
                                                Object fieldMapping,
                                                String fullyEnrolledDate) 
               {
                  Double score = null;
                  // If we have data, normalize it (max value of 1.0).
                  try
                  {
                     if (dataValue != null)
                     {
                        score = new Double(
                              Math.min(1.0, (Double.parseDouble(dataValue) / ACT_MAX_SCORE))
                           );
                     }
                  }
                  catch (NumberFormatException e)
                  {
System.err.println(e.toString()+" "+field+" : "+dataValue);
                  }

                  fieldMap.get("SAT_MATH")[1] = score;
                  // Added nothing to the dataVector
                  return dataVector;
               };
            }
         }
         );

/***
         put("ACT_COMP", new Object[] {
            // Same as "ACT_READ"
            (Double) (this.get("ACT_READ")[0]),
            (Double) (this.get("ACT_READ")[1]),
            (ComputeDataValue) (this.get("ACT_READ")[2])
         }
         );

         put("ACT_ENGL", new Object[] {
            // Same as "ACT_READ"
            (Double) (this.get("ACT_READ")[0]),
            (Double) (this.get("ACT_READ")[1]),
            (ComputeDataValue) (this.get("ACT_READ")[2])
         }
         );

         put("ACT_SCIRE", new Object[] {
            // Same as "ACT_READ"
            (Double) (this.get("ACT_READ")[0]),
            (Double) (this.get("ACT_READ")[1]),
            (ComputeDataValue) (this.get("ACT_READ")[2])
         }
         );
***/

         put("AGI", new Object[] {
            new Double(1000000.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("CASH_SAVINGS", new Object[] {
            new Double(1000000.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("PARENTS_MARRIED", new Object[] {
            new Double(3.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL1_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL1_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL2_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL2_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL3_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL3_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL4_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("SCHOOL4_ENROLL_PCT", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         put("GFU_SCHOOL_RANK", new Object[] {
            new Double(1.0), 
            new Double(0.0),
            // Same as "visits"
            (ComputeDataValue) (this.get("VISITS")[2])
         }
         );

         /**************************
          * What we're trying to predict
          **************************/
         put("ACAD_PROG_STATUS", new Object[] {
            new String[] {"AC"},
            new Double(0.0), // I.e., 0 if not attended
            // Same as "sex", except binary so only one option
            (ComputeDataValue) (this.get("SEX")[2])
         }
         );


         // Special case as we fetch demographics and determine
         // distance from 97132
         put("ZIP", new Object[] {
            null,
            new Double(0.0),
            new ComputeDataValue() 
            {
               public ArrayList<Double> compute(ArrayList<Double> dataVector, 
                                                String field, 
                                                String dataValue, 
                                                Object missing_value,
                                                Object fieldMapping,
                                                String fullyEnrolledDate) 
               {
                  final double MAX_AGE = 60.0; // Average for a zip
                  final double MAX_DISTANCE = 2000.0; // from 97132
                  final double MAX_POPULATION = 500000.0;   // 500K
                  final double MAX_HOUSING = 500000.0;      // $500K
                  final double MAX_INCOME = 250000.0;       // $250K

                  if (dataValue == null)
                  {
System.err.println("NULL value for "+field+" : "+dataValue);
                     dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value);
                     return dataVector;
                  }
            
                  String[] values = null;
                  ArrayList<Double> zipData;
                  double zipIncome = 0.0;

                  // If we already have data for this zipcode, don't
                  // look it up again
                  if (ZIP_CACHE.containsKey(dataValue))
                  {
                     dataVector.addAll(ZIP_CACHE.get(dataValue));
                     return dataVector;
                  }

                  zipData = new ArrayList<Double>();

                  Double distance = Zipcode.distanceBetween("97132", dataValue);
                  if (distance != null)
                  {
                     zipData.add(
                        Math.min(1.0, distance.doubleValue() / MAX_DISTANCE));
                  }
                  else
                  {
                     zipData.add( (Double) missing_value);
                  }

                  // Get census data for this zipcode
                  try
                  {
                     values = getCensusDataFor(dataValue);

                     // Values may be null if this is a Canadian zip or
                     // we couldn't find data for the zip
                     if (values == null)
                     {
                        zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value); zipData.add( (Double) missing_value);
                     }
                     else // should have data, so normalize it
                     {
                        // For age, normalize to N years old
                        try
                        {
                           zipData.add(
                                 Math.min(1.0,Double.parseDouble(values[0]) / MAX_AGE));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        // For population, normalize for max size
                        try
                        {
                           zipData.add(
                                 Math.min(1.0,Double.parseDouble(values[1]) / MAX_POPULATION));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        // For diversity, divide white by total 
                        try
                        {
                           zipData.add(Double.parseDouble(values[2]) / Double.parseDouble(values[1]));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        // For income , normalize for max size
                        try
                        {
                           // Keep track of the income as we cache it
                           // separately as well
                           zipIncome = Math.min(1.0,Double.parseDouble(values[3]) / MAX_INCOME);
                           zipData.add(zipIncome);
                           // If we had an income value, cache it for
                           // use as a missing value later
                           ZIP_INCOME_CACHE.put(dataValue, zipIncome);
                        }
                        catch (NumberFormatException e)
                        { 
                           zipIncome = ( (Double) missing_value).doubleValue();
                           zipData.add( (Double) missing_value);
                        }
                        // For house value, normalize for max value
                        try
                        {
                           zipData.add(
                                 Math.min(1.0,Double.parseDouble(values[4]) / MAX_HOUSING));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        // For educated, % of total population
                        try
                        {
                           zipData.add(Double.parseDouble(values[5]) / Double.parseDouble(values[1]));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        // Percentage for each type of population
                        try
                        {
                           zipData.add(Double.parseDouble(values[6]) / Double.parseDouble(values[5]));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        try
                        {
                           zipData.add(Double.parseDouble(values[7]) / Double.parseDouble(values[5]));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        try
                        {
                           zipData.add(Double.parseDouble(values[8]) / Double.parseDouble(values[5]));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        try
                        {
                           zipData.add(Double.parseDouble(values[9]) / Double.parseDouble(values[5]));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }
                        try
                        {
                           zipData.add(Double.parseDouble(values[10]) / Double.parseDouble(values[5]));
                        }
                        catch (NumberFormatException e)
                        { 
                           zipData.add( (Double) missing_value);
                        }

                     }

                     // Remember the data we got for this zip
                     ZIP_CACHE.put(dataValue, zipData);

                     dataVector.addAll(zipData);
                  }
                  catch (IOException e) // Bogus data, fill vector with missing values
                  {
// System.err.println(e.toString()+" CENSUS : "+dataValue);
                     // Note that we add 12 here because we're not using
                     // the zipData at all - that may already have a
                     // distance added which is why we only add 11
                     // things to zipdata above on an error
                     dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value); dataVector.add( (Double) missing_value);
                  }
                  return dataVector;
               }
            }
         }
         );

      }
   };


   /**
    * Get Census demographics via a web service. As some data is
    * missing, we'll try "adjacent" zips until we find some data (unless
    * the zip appears to be from Canada)
    *
    * @param zip to find data for
    *
    * @return array of Strings with census data
    */
   private static String[] getCensusDataFor(String zip) throws IOException
   {
      int zipIncrement = 1;
      String searchZip = zip;
      URL url = null;
      HttpURLConnection conn;
      Scanner response;
      MatchResult result;

      while (true)
      {
         // B01002_001E is median age
         // B01003_001E is total population
         // B02001_002E white
         // B19013_001E is household income
         // B25077_001E is median value owner-occupied housing
         // B06009_001E educated total - see below
         // B06009_002E less than high school
         // B06009_003E high school
         // B06009_004E AA
         // B06009_005E BS
         // B06009_006E Grad
         try
         {
            url = new URL(CENSUS_URL+CENSUS_YEAR+"/"+CENSUS_DATASET+"?key="+CENSUS_KEY+"&get=B01002_001E,B01003_001E,B02001_002E,B19013_001E,B25077_001E,B06009_001E,B06009_002E,B06009_003E,B06009_004E,B06009_005E,B06009_006E&for=zip+code+tabulation+area:"+searchZip); 

            conn = (HttpURLConnection) url.openConnection();            
            response = new Scanner(conn.getInputStream());
            response.findWithinHorizon("\\[(\\S+)\\]\\]",0);
            result = response.match();
            // Break the matching line up into an array of strings that
            // have no quotes or commas
            return result.group(1).replace('"',' ').split(",");
         }
         catch (IllegalStateException e) // Some zips have no data or may be from Canada!
         {
//System.err.println(e.toString()+" for census for "+searchZip+" : \n\t"+url);
            // If the zipcode appears to be from Canada or nothing close with data, return null;
            // nothing we can do
            if (Character.isLetter(searchZip.charAt(0)) || zipIncrement > 5)
            {
               return null;
            }
            // Otherwise it's US so try an "adjacent" zipcode until we get some data
            // Add the increment. If the increment is positive then negate it,
            // otherwise add 1 to it and make it positive for next time
//System.err.print(searchZip+" has no data, trying ");
            searchZip = String.format("%05d", Integer.parseInt(zip) + zipIncrement);
//System.err.println(searchZip+"...");
            zipIncrement = (zipIncrement > 0) ? zipIncrement*(-1) : zipIncrement*(-1)+1;
         }
      } // while
   } // getCensusDataFor



   /**
    * Patches the fieldMap by using the average SAT scores, retrieved from the database, for 
    * read/math/write as the "fieldMapping" values
    *
    * @param Connection to the database
    * @param fieldMap to patch
    */
   public static void setSATDefaults(Connection dbConn, HashMap<String, Object[]> fieldMap)
         throws NumberFormatException, SQLException
   {
      Statement query = null;
      ResultSet results = null;
      
      // Create a new query statement and execute the query 
      query = dbConn.createStatement();
      if ((results = query.executeQuery("select avg(score) as score from sysadm.PS_STDNT_TEST_COMP where test_id = 'SATI' and test_component = 'READ'")).next())
         fieldMap.get("SAT_READ")[0] = new Double(results.getDouble("score") / SAT_MAX_SCORE);
      results.close();

      if ((results = query.executeQuery("select avg(score) as score from sysadm.PS_STDNT_TEST_COMP where test_id = 'SATI' and test_component = 'MATH'")).next())
         fieldMap.get("SAT_MATH")[0] = new Double(results.getDouble("score") / SAT_MAX_SCORE);
      results.close();
/***
      if ((results = query.executeQuery("select average(score) as score from sysadm.PS_STDNT_TEST_COMP where test_id = 'SATI' and test_component = 'WRITE'")).next())
         fieldMap.get("TEST_WRITE")[0] = new Double(results.getDouble("score") / SAT_MAX_SCORE);
      results.close();
***/

   } //setSATDefaults


   /**
    * Method connects to the database, issues a simple query and
    * displays the results
    */
   public static void main(String[] args) throws NumberFormatException, IOException, ClassNotFoundException
   {
      Connection dbConn = null;
      Statement query = null;
      ResultSet results = null;
      boolean predicting = false;
      FileOutputStream outFile = null;
      FileOutputStream idFile = null;
      PrintStream output = System.out;
      PrintStream idOutput = null;
      int fromYear=0;
      int toYear=0;
      String filename = null;
      int numRows = 0;        
      int badRows = 0;
      boolean dataBad = false;
      FileInputStream zipFileIn = null;
      ObjectInputStream zipFileInput = null;
      FileOutputStream zipFileOut = null;
      ObjectOutputStream zipFileOutput = null;
      URL url;
      HttpURLConnection conn;
      // We always include the "fall" fields; others may be added below
      // based on switches
      ArrayList<String> outputFields = new ArrayList<String>(Arrays.asList(FALL_FIELDS));

      // Vector OF vectors of data
      ArrayList<ArrayList<Double>> outputData = new ArrayList<ArrayList<Double>>(50000);
      ArrayList<ArrayList<Double>> predictData = new ArrayList<ArrayList<Double>>(50000);
      // Vector of IDs
      ArrayList<String> ids = new ArrayList<String>(50000);


      // Explicitly load the JDBC driver
      // NOTE: An alternative is to execute this program with the
      // following switch: -Djdbc.drivers=<JDBC Driver String>
      Class.forName(JDBC_DRIVER);
      
      // Create a connection to the database
      try
      {
         dbConn = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);
         // Set the transaction isolation low so we don't worry about
         // setting or checking locks so we don't interfere with other
         // database activities; we're unlikely to see uncommitted data
         // and, even if we do, it's not likely to be a problem.
///         dbConn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
         dbConn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      }
      catch (SQLException e)
      {
         System.err.println("Unable to connect to database\n"+e.getMessage());
         System.exit(1);
      }

      for (int i=0; i<args.length; i++)
      {
         switch (args[i])
         {
            case "--predict":
               predicting = true; // Will determine how we process data below
               break;
            case "--output":
               filename = args[i+1];
               output = new PrintStream(outFile = new FileOutputStream(filename));
               break;
            case "--from":
               fromYear = Integer.parseInt(args[i+1]);
               break;
            case "--to":
               toYear = Integer.parseInt(args[i+1]);
               break;

            case "--fall":
               // Don't want prospects????
////               QUERY.append(" AND A.RECRUITING_STATUS != 'PROS' ");
               break;

            // Note that the following two cases DO fall through, each
            // adding additional attributes - if none of these were
            // specified, then we're getting "pre-application" data
            case "--admit":
               // Add more attributes to our set and only retrieve
               // data for those who have been admitted - note that the
               // attributes are added in the next label that does not
               // require students to have been admitted, but only
               // apparently in the process of applying
               outputFields.addAll(Arrays.asList(ADMIT_FIELDS));
               QUERY.append(" and D.ACTION_DT is not null ");

            case "--apply":
               // Add a more attributes to our set and only retrieve
               // data for those who have a completed application 
               outputFields.addAll(Arrays.asList(APPLY_FIELDS));
               QUERY.append(" and B.ADM_CREATION_DT is not null ");
               break;
         }

      }

      // Note that toYear and fromYear are not optional so make sure we
      // have those parameters
      if (fromYear==0 || toYear==0)
      {
         System.err.println("ERROR: Missing start or end year\n\t"+
               "USAGE: ExtractData --from <start year> --to <end year> [--predict] [--output <filename>] [--apply] [--admit]");
         System.exit(1);
      }

      // Add a clause to the where clause such that the "admit term" of
      // the student is in the set of terms we're looking for.
      // Basically, the term is a 4-digit number where the first is the
      // millimium (e.g., 2) the century is dropped (e.g., 0), the two
      // years are used (e.g., 13) followed by '1' for the first
      // semester. So fall of 2013 is 2131. 
      QUERY.append(" AND A.ADMIT_TERM in (");
      for (int year=fromYear; year<=toYear; year++)
      {
         if (year > fromYear)
         {
            QUERY.append(",");
         }
         QUERY.append("'" + Integer.toString(year/1000) + Integer.toString(year % 100) + "1'");
      }
      QUERY.append(") ");


      // Starting with the 'toYear' find the most recent Census data
      // source working backwards from the toYear to the earliest
      // available data
      for (int year=toYear; year >= MIN_CENSUS_YEAR && CENSUS_YEAR==0; year--)
      {
         // See if data exists for that year by making a connection to
         // the census site; if data does not exists, we'll get a 404
         // status back
         url = new URL(CENSUS_URL+year+"/"+CENSUS_DATASET);
         if ( ( (HttpURLConnection) url.openConnection() ).getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND)
         {
            CENSUS_YEAR = year;
            System.err.println("Using "+year+" census data...\n\n");
         }
      }
      // At this point we either found a year or we walked backwards
      // beyond the minimum year so set the CENSUS_YEAR to the minimum
      if (CENSUS_YEAR==0)
      {
         CENSUS_YEAR = MIN_CENSUS_YEAR;
      }

         

      // Attempt to load the ZIP_CACHE and ZIP_INCOME from a file, if it
      // exists
      try
      {
         zipFileIn = new FileInputStream(CENSUS_CACHE_DIR + CENSUS_YEAR + ".dat");
         zipFileInput = new ObjectInputStream(zipFileIn);
         ZIP_CACHE = (HashMap<String, ArrayList<Double> >) zipFileInput.readObject();
         ZIP_INCOME_CACHE = (HashMap<String, Double>) zipFileInput.readObject();
         zipFileIn.close();
      }
      // Ignore any error, and we'll just use the empty zip cache
      catch (IOException e) { }
      

      // If we're not predicting data, then include the fields we'll
      // predict later as input here for the learning phase
      if (!predicting)
      {
         outputFields.addAll(Arrays.asList(PREDICT_FIELDS));
      }
      // If we ARE predicting data and were given an output filename,
      // then create a matching filename to hold the ID's so we can
      // match up predicted data with the ID later
      else if (filename != null)
      {
         idOutput = new PrintStream(idFile = new FileOutputStream(filename+".ids"));
      }


      try
      {
         // We retrieve the average SAT scores from the database and set
         // the default values in the fieldMap table for those
         // attributes to the averages
         setSATDefaults(dbConn, fieldMap);

         // Create a new query statement and execute the query 
         query = dbConn.createStatement();
         results = query.executeQuery(QUERY.toString());

         // Loop over the result set until false is returned by next()
         // and create an instance of the datarow for each row returned
         while (results.next())
         {
            outputData.add(new ArrayList<Double>());

            // For each field we want to include in the output, add data from 
            // the database. We pass along the fully-enrolled-date as a number of date
            // attributes use that in their computation. 
            for (String fieldName : outputFields)
            {
               // One special case is AGI for FAFSA. 
               // pass the current zip's cached income data as the missing value instead
               ((ComputeDataValue)(fieldMap.get(fieldName)[2])).compute(
                        outputData.get(numRows), 
                        fieldName, 
                        results.getString(fieldName), 
                        (fieldName.equals("AGI") && 
                            results.getString("ZIP") != null &&
                            ZIP_INCOME_CACHE.containsKey(results.getString("ZIP"))) ? 
                                 ZIP_INCOME_CACHE.get(results.getString("ZIP")) : 
                                 fieldMap.get(fieldName)[1],
                        (fieldMap.get(fieldName)[0]),
                        results.getString("FULLY_ENRL_DT")
                    );
            }

            // When predicting, we write ID information to a separate
            // file so that we have a corresponding ordered file we can
            // use to map predicted values onto
            if (predicting && idFile != null) // Write ID's to a separate file?
            {
               predictData.add(new ArrayList<Double>());
               for (String fieldName : PREDICT_FIELDS)
               {
                  ((ComputeDataValue)(fieldMap.get(fieldName)[2])).compute(predictData.get(numRows), 
                                                              fieldName, 
                                                              results.getString(fieldName), 
                                                              (fieldMap.get(fieldName)[1]),
                                                              (fieldMap.get(fieldName)[0]),
                                                              results.getString("FULLY_ENRL_DT"));
               }
               ids.add(results.getString("EMPLID")+"\t"+results.getString("FIRST_NAME")+"\t"+
                       results.getString("LAST_NAME")+"\t"+results.getString("CITY")+"\t"+
                       results.getString("STATE"));
            }

System.err.print("\n"+numRows++);

         } // while results

         results.close();



         // Now print all the data. First we need to remove any bad data
         // and count the number of rows as our output file needs to
         // have a header with the number of data rows as required by
         // FANN
         for (int i=0; i < numRows; i++)
         {
            dataBad = false;
            // Print out the array of values space-delimited unless
            // there's some sort of garbage data in the row - in that
            // case, skip it
            for (int j=0; j < outputData.get(0).size() && !dataBad; j++)
            { 
               dataBad = outputData.get(i).get(j).isNaN() || 
                         outputData.get(i).get(j)==null;
            }
            if (dataBad)
            {
               badRows++;
               outputData.get(i).set(0, null); // Mark this row as bad
            }
         }

         // Output the number of rows, the size of the rows, and, if
         // not predicting, the number of outputs followed by the data
         if (numRows > 0)
         {
            output.printf("%d %d %d \n",
                  numRows-badRows, 
                  predicting ? 
                       outputData.get(0).size() : 
                       outputData.get(0).size() - PREDICT_FIELDS.length, 
                  predicting ? 0 : PREDICT_FIELDS.length);
         }

         // Now we actually output the data itself
         for (int i=0; i < numRows; i++)
         {
            if (outputData.get(i).get(0) != null) // Not marked as "bad"
            {
               // Print out the selected fields
               for (int j=0; j < outputData.get(0).size(); j++)
               {
                  output.print(outputData.get(i).get(j)+" ");
               }

               // If we ARE predicting and we have a file for IDs, then
               // include the actual DB values we're predicting here so
               // we can compare prediction to actual
               if (predicting && idFile != null) // Write ID's to a separate file?
               {
                  idOutput.print(ids.get(i));
                  // Output the actual values in the DB for fields that we
                  // predict
                  for (int j=0; j < predictData.get(i).size(); j++)
                  {
                     idOutput.print("\t"+predictData.get(i).get(j));
                  }
                  idOutput.println();
               }

               output.println();
            }
         }

System.err.println("Bad rows :"+badRows);

         if (idFile != null)
         {
            idFile.close();
         }
         if (outFile != null)
         {
            outFile.close();
         }

         // Close the connection to the database and files
         dbConn.close();
      }
      catch (SQLException e)
      {
         System.err.println(QUERY);
         System.err.println("Fatal database error\n"+e.getMessage());
         try
         {
            dbConn.close();
         }
         catch (SQLException x) {} // Nothing more to do, we're dying
         System.exit(1);
      }

      // Attempt to save the ZIP_CACHE and ZIP_INCOME to a file
      try
      {
         zipFileOut = new FileOutputStream(CENSUS_CACHE_DIR + CENSUS_YEAR + ".dat");
         zipFileOutput = new ObjectOutputStream(zipFileOut);
         zipFileOutput.writeObject(ZIP_CACHE);
         zipFileOutput.writeObject(ZIP_INCOME_CACHE);
         zipFileOut.close();
      }
      // Ignore any error, and we'll just use the empty zip cache
      catch (IOException e) { }

   } // main

}
