package com.esoft.jdp2p.user.service;

import java.util.List;

import com.esoft.archer.user.model.RechargeBankCard;
import com.esoft.archer.user.model.UserBill;
import com.esoft.archer.user.model.UserWealth;
import com.esoft.jdp2p.loan.model.Recharge;

/**
 * Filename:RechargeService.java
 * Description:充值接口
 * Copyright: Copyright(c)2013
 * Company:jdp2p
 * @author:gongph
 * version:1.0
 * Create at: 2014-1-4 下午04:16:53
 */
public interface RechargeService {
	
	/**
	 * 计算充值手续费
	 * @param amount 充值金额
	 * @return double
	 */
	public double calculateFee(double amount);

	/**
	 * 充值支付成功回调，可能回调多次
	 * @param rechargeId 充值编号
	 */
	public void rechargePaySuccess(String rechargeId);
	
	/**
	 * 管理员充值
	 * @param userBill 资金转移对象
	 */
	public void rechargeByAdmin(UserBill userBill);
	/**
	 * 管理员充值
	 * @param userWealth 资金转移对象
	 */
	public void rechargeByAdmin(UserWealth userWealth);
	
	/**
	 * 管理员充值（把充值置为成功）
	 * @param rechargeId 充值编号
	 */
	public void rechargeByAdmin(String rechargeId);
	/**
	 * 生成一个充值订单
	 * @return 充值url
	 */
	public String createRechargeOrder(Recharge recharge);
	
	/**
	 * 获取银行卡直连的列表
	 */
	public List<RechargeBankCard> getBankCardsList();

	/**
	 * 线下充值
	 * @param recharge
	 * @return
	 */
	String createOfflineRechargeOrder(Recharge recharge);
	
	/**
	 * 通过银行编号获取银行名称，比如  农业银行-ABC；工商银行-ICBC
	 * @param bankNo
	 * @return
	 */
	String getBankNameByNo(String bankNo);

	void rechargePaySuccessWealth(String rechargeId);
}
