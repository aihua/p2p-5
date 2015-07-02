package com.esoft.ipspay.loan.service.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.faces.context.FacesContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.hibernate.LockMode;
import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.esoft.core.annotations.Logger;
import com.esoft.core.util.DateStyle;
import com.esoft.core.util.DateUtil;
import com.esoft.core.util.Dom4jUtil;
import com.esoft.core.util.GsonUtil;
import com.esoft.core.util.ThreeDES;
import com.esoft.ipspay.trusteeship.IpsPayConstans;
import com.esoft.ipspay.trusteeship.IpsPayConstans.Config;
import com.esoft.ipspay.trusteeship.service.impl.IpsPayOperationServiceAbs;
import com.esoft.jdp2p.loan.LoanConstants;
import com.esoft.jdp2p.loan.exception.ExistWaitAffirmInvests;
import com.esoft.jdp2p.loan.exception.InvalidExpectTimeException;
import com.esoft.jdp2p.loan.model.Loan;
import com.esoft.jdp2p.loan.service.LoanService;
import com.esoft.jdp2p.trusteeship.TrusteeshipConstants;
import com.esoft.jdp2p.trusteeship.exception.TrusteeshipReturnException;
import com.esoft.jdp2p.trusteeship.model.TrusteeshipAccount;
import com.esoft.jdp2p.trusteeship.model.TrusteeshipOperation;
import com.esoft.jdp2p.trusteeship.service.impl.TrusteeshipOperationBO;

import cryptix.jce.provider.MD5;

@Service
public class IpsPayPassLoanApplyOperation extends
		IpsPayOperationServiceAbs<Loan> {

	@Resource
	HibernateTemplate ht;

	@Resource
	TrusteeshipOperationBO trusteeshipOperationBO;

	@Logger
	Log log;

	@Resource
	LoanService loanService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public TrusteeshipOperation createOperation(Loan loan,
			FacesContext facesContext) throws IOException {
		loan.setStatus(LoanConstants.LoanStatus.WAITING_VERIFY_AFFIRM);
		ht.update(loan);
		DecimalFormat currentNumberFormat = new DecimalFormat("#0.00");
		StringBuffer content = new StringBuffer();
		content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		content.append("<pReq>");

		// 商户订单号
		content.append("<pMerBillNo>" + loan.getId() + "</pMerBillNo>");
		// 标的号
		content.append("<pBidNo>" + loan.getId() + "</pBidNo>");
		// 商户日期
		content.append("<pRegDate>"
				+ DateUtil.DateToString(new Date(), DateStyle.YYYYMMDD)
				+ "</pRegDate>");
		// 借款金额
		String Amount = currentNumberFormat.format(loan.getLoanMoney());
		content.append("<pLendAmt>" + Amount + "</pLendAmt>");
		// 借款保证金
		// FIXME:需要根据实际情况取值
		// String Deposit = currentNumberFormat.format(loan.getDeposit());
		content.append("<pGuaranteesAmt>" + 0 + "</pGuaranteesAmt>");
		// 借款利率
		String pTrdLendRate = currentNumberFormat.format(loan.getRatePercent());
		content.append("<pTrdLendRate>" + pTrdLendRate + "</pTrdLendRate>");
		// 借款周期类型
		// FIXME:需要根据实际情况取值
		content.append("<pTrdCycleType>" + 3 + "</pTrdCycleType>");
		// 借款周期值
		content.append("<pTrdCycleValue>" + loan.getDeadline()
				+ "</pTrdCycleValue>");
		// 借款用途
		content.append("<pLendPurpose><![CDATA["
				+ (StringUtils.isEmpty(loan.getLoanPurpose()) ? "系统借款" : loan
						.getLoanPurpose()) + "]]></pLendPurpose>");
		// 还款方式:1：等额本息，2：按月还息到期还本，99：其他；
		content.append("<pRepayMode>99</pRepayMode>");
		// 标的操作类型，1：新增，2：结束
		content.append("<pOperationType>" + 1 + "</pOperationType>");
		// 借款人手续费
		content.append("<pLendFee>"
				+ currentNumberFormat.format(loan.getLoanGuranteeFee())
				+ "</pLendFee>");
		// 账户类型 :0#机构（暂未开放） ；1#个人
		content.append("<pAcctType>" + 1 + "</pAcctType>");
		// 证件号码
		content.append("<pIdentNo>" + loan.getUser().getIdCard()
				+ "</pIdentNo>");
		// 证件号码
		content.append("<pRealName><![CDATA[" + loan.getUser().getRealname()
				+ "]]></pRealName>");
		// IPS账户号 :账户类型为 1 时，IPS 托管账户号（个人）; 账户类型为 0 时，由 IPS 颁发的商户号
		content.append("<pIpsAcctNo>"
				+ ht.get(TrusteeshipAccount.class, loan.getUser().getId())
						.getAccountId() + "</pIpsAcctNo>");
		content.append("<pWebUrl><![CDATA["
				+ IpsPayConstans.ResponseWebUrl.PRE_RESPONSE_URL
				+ IpsPayConstans.OperationType.LOAN + "]]></pWebUrl>");
		content.append("<pS2SUrl><![CDATA["
				+ IpsPayConstans.ResponseS2SUrl.PRE_RESPONSE_URL
				+ IpsPayConstans.OperationType.LOAN + "]]></pS2SUrl>");

		// 备注
		content.append("<pMemo1></pMemo1>");
		content.append("<pMemo2></pMemo2>");
		content.append("<pMemo3></pMemo3>");
		content.append("</pReq>");
		if (log.isDebugEnabled()) {
			log.debug("标的登记发送的信息：" + content.toString());
		}
		String arg3DesXmlPara = ThreeDES.encrypt(content.toString(),
				Config.THREE_DES_BASE64_KEY, Config.THREE_DES_IV,
				Config.THREE_DES_ALGORITHM);
		String argSign = new MD5().toMD5(
				IpsPayConstans.Config.MER_CODE + arg3DesXmlPara
						+ IpsPayConstans.Config.CERT).toLowerCase();
		// 包装参数
		Map<String, String> params = new HashMap<String, String>();
		params.put("pMerCode", IpsPayConstans.Config.MER_CODE);
		params.put("p3DesXmlPara", arg3DesXmlPara);
		params.put("pSign", argSign);

		TrusteeshipOperation to = createTrusteeshipOperation(loan.getId(),
				IpsPayConstans.RequestUrl.LOAN, loan.getId(),
				IpsPayConstans.OperationType.LOAN, params);

		try {
			sendOperation(to, facesContext);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return to;
	}

	@Override
	@Transactional
	public void receiveOperationPostCallback(ServletRequest request)
			throws TrusteeshipReturnException {
		try {
			request.setCharacterEncoding("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		// 商户号
		String pMerCode = request.getParameter("pMerCode");
		// 标状态
		String pErrCode = request.getParameter("pErrCode");
		// 返回信息
		String pErrMsg = request.getParameter("pErrMsg");
		// 获取返回的3DES报文体加密
		String p3DesXmlPara = request.getParameter("p3DesXmlPara");
		// 获取返回的加密摘要
		String pSign = request.getParameter("pSign");
		if (log.isDebugEnabled()) {
			log.debug("p3DesXmlPara------------>" + p3DesXmlPara);
		}
		// 解密
		String pXmlPara = ThreeDES.decrypt(p3DesXmlPara,
				Config.THREE_DES_BASE64_KEY, Config.THREE_DES_IV,
				Config.THREE_DES_ALGORITHM);
		// 处理账户开通成功
		Map<String, String> resultMap = Dom4jUtil.xmltoMap(pXmlPara);
		String loanId = resultMap.get("pBidNo");
		String pOperationType= resultMap.get("pOperationType");

		TrusteeshipOperation to;
		if (pOperationType.equals("1")) {
			to = trusteeshipOperationBO.get(
					IpsPayConstans.OperationType.LOAN, loanId, "ips");
		} else if (pOperationType.equals("2")) {
			to = trusteeshipOperationBO.get(
					TrusteeshipConstants.OperationType.CANCEL_LOAN, loanId, "ips");
		} else {
			throw new RuntimeException("unknown pOperationType:"+pOperationType);
		}
		to.setResponseTime(new Date());
		to.setResponseData(GsonUtil.fromMap2Json(resultMap));
		Loan loan = ht.get(Loan.class, loanId, LockMode.UPGRADE);
		if (log.isDebugEnabled()) {
			log.debug("pErrCode:" + pErrCode + "********标的登记返回信息********");
			log.debug("pErrMsg:" + pErrMsg);
		}

		if ("MG02501F".equals(pErrCode)) {
			if (loan.getStatus().equals(
					LoanConstants.LoanStatus.WAITING_VERIFY_AFFIRM)) {
				loan.setStatus(LoanConstants.LoanStatus.RAISING);
				try {
					loanService.passApply(loan);
					to.setStatus(TrusteeshipConstants.Status.PASSED);
				} catch (InvalidExpectTimeException e) {
					if (log.isErrorEnabled()) {
						log.error(e);
						e.printStackTrace();
					}
				}
			}
		} else if ("MG02500F".equals(pErrCode)) {
			// 标的新增
		} else if ("MG02505F".equals(pErrCode)) {
			if (loan.getStatus().equals(
					LoanConstants.LoanStatus.WAITING_CANCEL_AFFIRM)) {
				try {
					loanService.fail(loan.getId(), "ips");
					to.setStatus(TrusteeshipConstants.Status.PASSED);
				} catch (ExistWaitAffirmInvests e) {
					if (log.isErrorEnabled()) {
						log.error(e);
						e.printStackTrace();
					}
				}
			}
		} else if ("MG02503F".equals(pErrCode)) {
			// 标的结束处理中
		} else {
			fail(to);
			throw new TrusteeshipReturnException(pErrMsg);
		}

	}

	@Transactional(rollbackFor = Exception.class)
	public void fail(TrusteeshipOperation to) {
		Loan loan = ht.get(Loan.class, to.getMarkId(), LockMode.UPGRADE);
		loan.setStatus(LoanConstants.LoanStatus.WAITING_VERIFY);
		to.setStatus(TrusteeshipConstants.Status.REFUSED);
		ht.update(to);
		ht.update(loan);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void receiveOperationS2SCallback(ServletRequest request,
			ServletResponse response) {
		try {
			receiveOperationPostCallback(request);
		} catch (TrusteeshipReturnException e) {
			e.printStackTrace();
		}
	}

}
