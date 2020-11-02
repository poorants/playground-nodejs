package com.sinsiway.petra.common.controller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.sinsiway.petra.cipher.util.PcaException;
import com.sinsiway.petra.cipher.util.PcaSession;
import com.sinsiway.petra.cipher.util.PcaSessionPool;
import com.sinsiway.petra.common.code.CommonCodes;
import com.sinsiway.petra.common.code.ErrorCodes;
import com.sinsiway.petra.common.dto.ConnectionUserDto;
import com.sinsiway.petra.common.dto.PdstUserDto;
import com.sinsiway.petra.common.service.CommonServiceImpl;
import com.sinsiway.petra.common.service.JwtService;
import com.sinsiway.petra.common.util.CommonUtils;
import com.sinsiway.petra.common.util.Converter;
import com.sinsiway.petra.common.util.LogWriter;
import com.sinsiway.petra.common.util.PetraConnectionManager;
import com.sinsiway.petra.common.util.PetraConnectionProperties;
import com.sinsiway.petra.common.util.SessionContextHolder;
import com.sinsiway.petra.mail.service.MailSendServiceImpl;

@Controller
public class CommonController {
	
	private static final Logger logger = LoggerFactory.getLogger(CommonController.class);
	
	@Autowired
	CommonServiceImpl CommonService;
	
	@Autowired
	MailSendServiceImpl mailSendService;
	
	@Autowired
	JwtService jwtService;
	
	@RequestMapping("/favicon.ico")
    @ResponseBody
    void favicon() {}
	
	@RequestMapping(value = "/init.do", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
	public ModelAndView init(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("login");
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/home.do", method = RequestMethod.GET)
	public ModelAndView home(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("home");
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/accessHome.do", method = RequestMethod.GET)
	public ModelAndView accessHome(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("acc/accHome/layout");
		
		return modelAndView;
	}

	@RequestMapping(value = "/sqlTools.do", method = RequestMethod.GET)
	public ModelAndView SQLTools(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("acc/home/layout");
		SessionContextHolder.remove();
		return modelAndView;
	}
	
	@RequestMapping(value = "/fileCipherHome.do", method = RequestMethod.GET)
	public ModelAndView fileCipherHome(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("fcp/home/layout");
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/fingerHome.do", method = RequestMethod.GET)
	public ModelAndView fingerHome(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("fpt/home/layout");
		
		return modelAndView;
	}

	@RequestMapping(value = "/initContPage.do")
	public ModelAndView initContPage(HttpServletRequest request, ModelAndView modelAndView, String menuType, String mainItemId, String subItemId) {
		modelAndView.setViewName(menuType);
		
		logger.info(menuType);
		
		modelAndView.addObject("mainItemId",mainItemId);
		modelAndView.addObject("subItemId",subItemId);
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/initSettingsContent.do")
	public ModelAndView initSettingsContent(HttpServletRequest request, ModelAndView modelAndView, String settingType) {
		modelAndView.setViewName(settingType);
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/login", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> login(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		String token = request.getHeader("Authorization");
		
		if(token == null || "".equals(token)){
			token = jwtService.create(requestMap.get("USER_NAME").toString(), 1000*60*60);	//기본 만료시간 1시간
		}else{
			Object jwt = jwtService.verifyToken(token);
			if(jwt instanceof ErrorCodes) {
				return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)jwt).getMap()), HttpStatus.BAD_REQUEST);
			}
		}
		
//		SessionContextHolder.set(request.getSession(), token); 
//		PetraConnectionManager.getInstance().initDBConnection();
		
		/* 2020.05.20 인증서 잠금 기능 모니터링 변경으로 인해 주석 처리 */
//		if(requestMap.containsKey("LOGIN_TYPE") && (int)requestMap.get("LOGIN_TYPE") == 2) {
//			PetraConnectionManager.getInstance().initDBConnectionCert(request.getSession(), requestMap);
//		} else {
//			PetraConnectionManager.getInstance().initDBConnection();
//		}
		
		if(requestMap.get("TIMEOUT") != null) {
			int timeout = (int) requestMap.get("TIMEOUT");
			
			request.getSession().setMaxInactiveInterval(timeout*60);
		}else {
			request.getSession().setMaxInactiveInterval(30*60);	//기본 설정값 10분
		}
		
		SessionContextHolder.remove();
		Object result = CommonService.loginVerification(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		int prodType = (int) requestMap.get("PROD_TYPE");
		
		if(prodType == 0) {
			((ConnectionUserDto)result).setREDIRECT_URL(request.getContextPath() + "/fileCipherHome.do");
			((ConnectionUserDto)result).setJWT(token);
			LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 공통 > 로그인");
		}else if(prodType == 1) {
			((ConnectionUserDto)result).setREDIRECT_URL(request.getContextPath() + "/fingerHome.do");
			((ConnectionUserDto)result).setJWT(token);
		}else if(prodType == 2) {
//			Map<String, Object> clientCheckMap = new HashMap<>();
//			clientCheckMap.put("PTU_ID", ((ConnectionUserDto)result).getPTU_ID());
//			clientCheckMap.put("CLIENT_STAT", requestMap.get("CLIENT_STAT"));
//			clientCheckMap.put("AUTH_STAT", requestMap.get("AUTH_STAT"));
//			boolean clientStat = CommonService.getClientStat(clientCheckMap);
//			
//			
//			if(clientStat) {
//				System.out.println("=================================================");
//				System.out.println("client Stat true 클라이언트 확인");
//				//클라이언트 설치 확인 클라이언트 정보 가져오기
//				
//				Map<String, Object> authResultMap = (Map<String, Object>) CommonService.getAuthStat(clientCheckMap);
//				if((boolean) authResultMap.get("AUTH_STAT")) {
//					System.out.println("=================================================");
//					System.out.println("auth Stat true 인증 통과");
//					
////					requestMap.put("CLIENT_MAC_ADDRESS", authResultMap.get("CLIENT_MAC_ADDRESS"));
////					requestMap.put("CLIENT_HOSTNAME", authResultMap.get("CLIENT_HOSTNAME"));
////					requestMap.put("CLIENT_OS_USER", authResultMap.get("CLIENT_OS_USER"));
////					requestMap.put("AUTH_SYSTEM_INFO", authResultMap.get("AUTH_SYSTEM_INFO"));
////					requestMap.put("WEB_SESS_ID", request.getRequestedSessionId());
//					
//					ConnectionUserDto data = (ConnectionUserDto) result;
//					requestMap.put("PTU_ID", data.getPTU_ID());
//					requestMap.put("REG_TYPE", 1);
//					requestMap.put("AUTH_USER", data.getUSER_ID());
//					requestMap.put("CLIENT_COMM_IP", CommonUtils.getInstance().getIpAddress(request));
//					requestMap.put("CLIENT_CHECK_RESULT", 3);
//					CommonService.changeAuthStatus(requestMap);
//					((ConnectionUserDto)result).setREDIRECT_URL(request.getContextPath() + "/accessHome.do");
//				}else {
//					System.out.println("=================================================");
//					System.out.println("auth Stat false 인증 실패");
//					requestMap.put("CLIENT_CHECK_RESULT", 2);
//				}
//				
//			}else{
//				System.out.println("=================================================");
//				System.out.println("client Stat false 클라이언트 미확인");
//				requestMap.put("CLIENT_CHECK_RESULT", 1);
//			}
		
			//원본
			ConnectionUserDto data = (ConnectionUserDto) result;
			requestMap.put("PTU_ID", data.getPTU_ID());
			requestMap.put("REG_TYPE", 1);
			requestMap.put("AUTH_USER", data.getUSER_ID());
			requestMap.put("CLIENT_COMM_IP", CommonUtils.getInstance().getIpAddress(request));

			try {
				InetAddress local;
				local = InetAddress.getLocalHost();
				String ip = local.getHostAddress();
				requestMap.put("CLIENT_MAC_ADDRESS", CommonUtils.getInstance().getMacAddress(ip));
				requestMap.put("CLIENT_HOSTNAME", InetAddress.getLocalHost().getHostName());
				requestMap.put("CLIENT_OS_USER", System.getProperty("user.name"));
				requestMap.put("WEB_SESS_ID", request.getSession().getId());
				requestMap.put("AUTH_SYSTEM_INFO","Global Authorization");
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			
			CommonService.changeAuthStatus(requestMap);

			SessionContextHolder.set(request.getSession(), token); 
			SessionContextHolder.get().setAttribute("connectionUserInfo", requestMap);
			
			((ConnectionUserDto)result).setREDIRECT_URL(request.getContextPath() + "/accessHome.do");
			((ConnectionUserDto)result).setJWT(token);
		}else if(prodType == 3) {
			((PdstUserDto)result).setREDIRECT_URL(request.getContextPath() + "/sqlTools.do");
			((PdstUserDto)result).setJWT(token);
		}else if(prodType == 4){
			((ConnectionUserDto)result).setREDIRECT_URL(request.getContextPath() + "/fingerHome.do");
			((ConnectionUserDto)result).setJWT(token);
		}else if(prodType == 5){
			((ConnectionUserDto)result).setREDIRECT_URL(request.getContextPath() + "/monitoring.do");
			((ConnectionUserDto)result).setJWT(token);
		}
		
		
		/* 2020.05.20 인증서 잠금 기능 모니터링 변경으로 인해 주석 처리 */
//		if(requestMap.containsKey("LOGIN_TYPE") && (int)requestMap.get("LOGIN_TYPE") == 2) {
//			DatabaseDto connectionInfo = (DatabaseDto) SessionContextHolder.get().getAttribute("CONNECTION_INFO");
//			PetraConnectionManager.getInstance().disconnection(SessionContextHolder.get().getId()+token, connectionInfo.toString());
//		}
		
//		Map<String, Object> resultMap = new HashMap<>();
//		resultMap.put("result", result);
//		resultMap.put("resultMap", requestMap);
//		
//		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), resultMap), HttpStatus.OK);
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	
	@RequestMapping(value = "/logout", method = RequestMethod.DELETE, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> logout(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		PetraConnectionManager.getInstance().initDBConnection(); 

		if(requestMap.containsKey("AUTH_USER")) {
//			PetraConnectionManager.getInstance().getMasterConnection();
			requestMap.put("CLIENT_COMM_IP", CommonUtils.getInstance().getIpAddress(request));
			try {
				InetAddress local;
				local = InetAddress.getLocalHost();
				String ip = local.getHostAddress();
				requestMap.put("CLIENT_MAC_ADDRESS", CommonUtils.getInstance().getMacAddress(ip));
				requestMap.put("CLIENT_HOSTNAME", InetAddress.getLocalHost().getHostName());
				requestMap.put("CLIENT_OS_USER", System.getProperty("user.name"));
				requestMap.put("WEB_SESS_ID", request.getSession().getId());
				requestMap.put("AUTH_SYSTEM_INFO","Global Authorization");
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			CommonService.changeAuthStatus(requestMap);
		}
		
		request.getSession().invalidate();
		SessionContextHolder.remove();
		
		Map<String, Object> responseData = new HashMap<>();
		responseData.put("REDIRECT_URL", request.getContextPath()+"/init.do");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), responseData), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/admin/history", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> adminHistory(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.adminHistory(request, (String)requestMap.get("DESC"));
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/cert", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> createCert(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.createCert(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 인증서 > " + requestMap.get("USER_NAME")+ " 신청 완료");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/cert/parse", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> parseCert(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.getCertParseData(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/cert/import", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> importCert(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.getCertParseData(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 인증서 > " + requestMap.get("USER_NAME")+ " 가져오기 성공");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/cert", method = RequestMethod.DELETE, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> delCert(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.delCertItem(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 인증서 > " + requestMap.get("USER_NAME")+ " 삭제");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/cert", method = RequestMethod.PATCH, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> renewCert(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.renewCertItem(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 인증서 > " + requestMap.get("USER_NAME")+ " 갱신");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/user/name/exists", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> existsUserName(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.existsUserName(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/user", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> reqAccount(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		requestMap.put("REQUEST_IP", CommonUtils.getInstance().getIpAddress(request));
		String authKey = mailSendService.genarateKey(false, 24);
		requestMap.put("AUTH_KEY", authKey);
		
		Object result = CommonService.reqAccount(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		Properties prop = CommonUtils.getInstance().getFileProperties("/properties/application.properties");
		int prodType = Integer.parseInt(prop.get("app.prod.type")+"");
		
		if(prodType == CommonCodes.DATA_STUDIO.getCode()) {
			String content = mailSendService.getAuthUserContent(requestMap.get("USER_NAME").toString(), requestMap.get("ORG_UID").toString(), authKey);
			mailSendService.sendMail(requestMap.get("EMAIL").toString(), "[Sinsiway] 이메일 주소를 인증해 주시기 바랍니다.", content);
		}
		 
//		result = CommonService.clientStat(requestMap);
//		if(result instanceof ErrorCodes) {
//			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
//		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/user/mail", method = RequestMethod.PATCH, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public  ResponseEntity<?> accessAuthMail(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.accessAuthMail(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/user/mail", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public  ResponseEntity<?> authUserMail(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.checkUserAuthKey(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/org", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getOrgList(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.getOrgList(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/user/request/status", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getReqStatusList(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		Object result = CommonService.getReqStatusList(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/publicKey", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> initPublicKeyList(HttpServletRequest request) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.getPublicKeyList();
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/publicKey", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> addRsaKey(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.addRsaKey(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 키 환경 > 개인키/공개키 관리 > 개인키 "+requestMap.get("KEY_NAME")+"("+requestMap.get("PREV_RSA_KEY_ID")+") 등록");
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 키 환경 > 개인키/공개키 관리 > 공개키 "+requestMap.get("KEY_NAME")+"("+requestMap.get("RSA_KEY_ID")+") 등록");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/publicKey", method = RequestMethod.DELETE, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> delRsaKey(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.delRsaKey(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		int keyType = (int) requestMap.get("KEY_TYPE");
		if(keyType == 1) {
			LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 키 환경 > 개인키/공개키 관리 > 개인키 "+requestMap.get("KEY_NAME")+"("+requestMap.get("RSA_KEY_ID")+") 삭제");
		}else {
			LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 키 환경 > 개인키/공개키 관리 > 공개키 "+requestMap.get("KEY_NAME")+"("+requestMap.get("RSA_KEY_ID")+") 삭제");
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/publicKey/import", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> importRsaKey(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.importRsaKey(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		int keyType = (int) requestMap.get("KEY_TYPE");
		
		if(keyType == 1) {
			LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 키 환경 > 개인키/공개키 관리 > 개인키 "+requestMap.get("KEY_NAME")+"("+requestMap.get("RSA_KEY_ID")+") 가져오기 성공");
		}else {
			LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 키 환경 > 개인키/공개키 관리 > 공개키 "+requestMap.get("KEY_NAME")+"("+requestMap.get("RSA_KEY_ID")+") 가져오기 성공");
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/encrypt/parameter", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> initParamTable(HttpServletRequest request) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.getParamList();
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/encrypt/parameter/validate", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> validateParam(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.validateParam(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/encrypt/parameter", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> addParam(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.addEncryptParam(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 기타 > 파라미터 관리 > 파라미터 "+requestMap.get("PARAM_NAME")+"("+requestMap.get("CRYPT_PARAM_ID")+") 등록");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/encrypt/parameter", method = RequestMethod.PATCH, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> modParam(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.modEncryptParam(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 기타 > 파라미터 관리 > 파라미터 "+requestMap.get("PARAM_NAME")+"("+requestMap.get("CRYPT_PARAM_ID")+") 변경");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/encrypt/parameter", method = RequestMethod.DELETE, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> delParam(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.delEncryptParam(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 기타 > 파라미터 관리 > 파라미터 "+requestMap.get("PARAM_NAME")+"("+requestMap.get("CRYPT_PARAM_ID")+") 삭제");
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/settings/encrypt/parameter/import", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> importParam(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Object result = CommonService.existsParamName(requestMap);
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		if((int)result > 0) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ErrorCodes.EXISTS_ENC_PARAM_NAME.getMap()), HttpStatus.BAD_REQUEST);
		}else {
			result = CommonService.addEncryptParam(requestMap);
			if(result instanceof ErrorCodes) {
				return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
			}
			
			LogWriter.getInstance().write(CommonCodes.SOHA, request, "# 환경 설정 > 기타 > 파라미터 관리 > 파라미터 "+requestMap.get("PARAM_NAME")+"("+requestMap.get("CRYPT_PARAM_ID")+") 가져오기 성공");
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/dbProperties.do", method = RequestMethod.POST)
	public ModelAndView masterInfo(HttpServletRequest request, ModelAndView modelAndView, @RequestBody Map<String, Object> requestMap) {
		
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));

		Properties prop = CommonUtils.getInstance().getFileProperties("/config/jdbc.properties");
		Map<String, Object> map = new HashMap<>();
		map.put("SERVICE_NAME", prop.getProperty("SERVICE_NAME"));
		map.put("IP", prop.getProperty("IP"));
		map.put("PORT", prop.getProperty("PORT"));
		
		modelAndView.addObject("returnData", map);
		 
		modelAndView.setViewName("jsonView");
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/emergencyExit.do", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> emergencyExit(HttpServletRequest request, ModelAndView modelAndView, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		if(requestMap.containsKey("AUTH_USER")) {
			SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
			requestMap.put("CLIENT_COMM_IP", CommonUtils.getInstance().getIpAddress(request));
			CommonService.changeAuthStatus(requestMap);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@Autowired 
	private ResourceLoader resourceLoader;
	
	@RequestMapping(value = "/login/clientDownload.do", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> clientDownload(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		File file = null;
		String filePath = null;
		try {																//임시 파일
			filePath = resourceLoader.getResource("classpath:../../resources/client/client.exe").getURI().getPath();
			
			file = new File(filePath);
			System.out.println("filePath : " + filePath);
			System.out.println("fileExist : " + file.exists() + "\n\n");

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		requestMap.put("URL", filePath);
		
		file = new File(filePath);
		
		System.out.println("fileExist : " + file.exists());
		
		System.out.println("fileGetPath : " + file.getPath());
		
		String out = new String();
        FileInputStream fis = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
        fis = new FileInputStream(file);
     
        int len = 0;
        byte[] buf = new byte[1024];
        
    	while ((len = fis.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
 
        byte[] fileArray = baos.toByteArray();
        out = new String(Base64.encodeBase64(fileArray));
 
        fis.close();
        baos.close();
        
        } catch (IOException e) {
            logger.info("Exception position : FileUtil - fileToString(File file)");
        }
        
        requestMap.put("CLIENT_BASE64", out);
        
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), requestMap), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/common/connectionInfo", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> initPetraConnectionProperties(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		Object result = PetraConnectionProperties.getInstance().initProperties();
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/getSessionId.do", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getSessionID(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		String text = request.getRequestedSessionId();
		System.out.println("=========================" + text + "=========================");
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/post/test.do", method = RequestMethod.POST, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> postTest(HttpServletRequest request, @RequestBody Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		String text = (String) requestMap.get("TEXT");
		System.out.println("=========================" + text + "=========================");
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/get/test.do", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getTest(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		PetraConnectionManager.getInstance().initDBConnection(); 
		
		String text = (String) requestMap.get("TEXT");
		System.out.println("=========================" + text + "=========================");
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), new HashMap<>()), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/common/test/ping", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> pingTest(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		String ip = requestMap.get("IP").toString();
		int timeout = Integer.parseInt(requestMap.get("TIME_OUT")+"") * 1000;
		Map<String, Object> responseData = new HashMap<>();
		
		try {
			InetAddress ping = InetAddress.getByName(ip);
			boolean reachable =  ping.isReachable(timeout);
 
			if (reachable) {
				responseData.put("RESULT", "테스트 성공");
			} else {
				responseData.put("RESULT", "테스트 실패");
			}
 
		} catch (Exception e) {
			e.printStackTrace();
			responseData.put("RESULT", "테스트 실패");
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), responseData), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/common/test/connect", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> connTest(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		
		Map<String, Object> responseData = new HashMap<>();
		if(requestMap.containsKey("IS_ENC_PWD") ) {
		}else {
			try {
				String password = (String) requestMap.get("PASSWORD");
				String encPassword = PcaSessionPool.getSession().encrypt(password);
				requestMap.put("PASSWORD", encPassword);
			} catch (PcaException e1) {
				e1.printStackTrace();
			}
		}
		
		Object result = PetraConnectionManager.getInstance().initDBConnection(requestMap);
		if(result instanceof Exception || result instanceof ErrorCodes) {
			responseData.put("RESULT", "접속 실패");
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), responseData), HttpStatus.OK);
		}
		
		result = CommonService.connectionTest(requestMap);
		if(result instanceof Exception || result instanceof ErrorCodes) {
			responseData.put("RESULT", "접속 실패");
		}else {
			responseData.put("RESULT", "접속 성공");
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), responseData), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/common/create/hv", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> createHv(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		String token = request.getHeader("Authorization");
		String hashValue;
		try {
			PcaSession cryptSession = PcaSessionPool.getSession();
			hashValue = cryptSession.encrypt("sha_512_b64",  token);
		} catch (Exception e) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((Exception)e).getMessage()), HttpStatus.BAD_REQUEST);
			
		}
		logger.info("create hv : " + hashValue);
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), hashValue), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/insertDownloadStat", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> insertDownloadStat(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		PetraConnectionManager.getInstance().initDBConnection();
		
		requestMap.put("CLIENT_COMM_IP", CommonUtils.getInstance().getIpAddress(request));
		
		Object result = CommonService.insertDownloadStat(requestMap);
		
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/login/getDownloadStat", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getDownloadStat(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		SessionContextHolder.set(request.getSession(), request.getHeader("Authorization"));
		PetraConnectionManager.getInstance().initDBConnection();
		requestMap.put("CLIENT_COMM_IP", CommonUtils.getInstance().getIpAddress(request));
		
		System.out.println(requestMap.get("CLIENT_COMM_IP"));
		
		Object result = CommonService.getDownloadStat(requestMap);
		
		if(result instanceof ErrorCodes) {
			return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), ((ErrorCodes)result).getMap()), HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/table_test1", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
	public ModelAndView testTable(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("acc/editor/table_test");
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/table_test2", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
	public ModelAndView testTable2(HttpServletRequest request, ModelAndView modelAndView) {
		modelAndView.setViewName("acc/editor/table_test_bak");
		
		return modelAndView;
	}
	
	@RequestMapping(value = "/cipher/enc/sha", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getShaEncryptText(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		String plainText = (String) requestMap.get("plain_text");
		String result = null;

		try{

			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(plainText.getBytes());
			byte[] hash = md.digest();
			StringBuffer hexString = new StringBuffer();

			for (int i = 0; i < hash.length; i++) {
				String hex = Integer.toHexString(0xff & hash[i]);
				if(hex.length() == 1) hexString.append('0');
				hexString.append(hex);
			}

			
			result = hexString.toString();
			//출력

		} catch(Exception ex){
			throw new RuntimeException(ex);
		}

		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/cipher/enc/aria", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getEncryptText(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		String plainText = (String) requestMap.get("plain_text");
		Object result = null;
		try {
			result = PcaSessionPool.getSession().encrypt(plainText);
		} catch (PcaException e) {
			e.printStackTrace();
		}
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
	
	@RequestMapping(value = "/cipher/dec/aria", method = RequestMethod.GET, produces= {"application/json;charset=UTF-8", "application/xml;charset=UTF-8"})
	public ResponseEntity<?> getDecryptText(HttpServletRequest request, @RequestParam Map<String, Object> requestMap) {
		String encText = (String) requestMap.get("encrypt_text");
		Object result = null;
		try {
			result = PcaSessionPool.getSession().decrypt(encText);
		} catch (PcaException e) {
			e.printStackTrace();
		}
		return new ResponseEntity<>(Converter.getInstance().convert(request.getHeader("Accept"), result), HttpStatus.OK);
	}
}