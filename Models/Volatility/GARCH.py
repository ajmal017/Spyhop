#!/usr/bin/python3

"""GARCH.py
Description:
    estimate daily volatility using GARCH(p,q) model. The estimated volatility is validated
    using intraday bar data
"""

import numpy as np
import pandas as pd
import traceback
import matplotlib.pyplot as plt
from scipy import optimize
from scipy import stats
from Preprocess import Preprocess
import Postprocess as post


class GARCH:

    """constructor
    """
    def __init__(self, p, q, lag):
        self.preprocess = Preprocess(lag=lag)
        self.p = p  # order of residual term
        self.q = q  # order of variance term
        self.omega = np.array([])
        self.alpha = np.empty(shape=(0, self.p))  # residual term parameter
        self.beta = np.empty(shape=(0, self.q))  # variance term parameter

    """
        Description:
            prepare all bar data into a DataFrame organized by symbols, residual and mean
        Output:
            formatedData: the DataFrame with processed residual data
    """
    def prep_data(self):
        daily_price = self.preprocess.retrieve_open_close()
        daily_change = post.compute_daily_change(daily_price).fillna(0)
        mu = daily_change.mean()
        residual = daily_change.subtract(mu)
        return residual

    """
        Description:
            the cost function of GARCH parameter fit
        Input:
            theta: array holding GARCH parameters [omega, [p], [q]]
            resi: price residual
        Output:
            cost of model
    """
    def maximum_likelihood(self, theta, resi):
        vari = self.garch(theta, resi)
        negative_likelihood = np.log(2*np.pi*vari)+np.divide(np.power(resi, 2), vari)
        return np.sum(negative_likelihood)

    """
        Description:
            GARCH model for volatility estimation
        Input:
            theta: array holding GARCH parameters [omega, [p], [q]]
            resi: price residual
        Output:
            vari: computed series of variance
    """
    def garch(self, theta, resi):
        omega = theta[0]
        alpha = theta[1:-self.q]
        beta = theta[self.p+1:]
        size = resi.shape[0]
        vari = np.zeros(shape=size)
        # initialize variance as the overall residual series variance
        vari[0] = np.var(resi, axis=0)
        for r in range(size-1):
            pterm = 0
            for i in range(len(alpha)):
                if r-i >= 0:  # reduce order at start
                    pterm += alpha[i] * np.power(resi[r-i], 2)
            qterm = 0
            for j in range(len(beta)):
                if r-j >= 0:  # reduce order if necessary, starting order 1
                    qterm += beta[j] * vari[r-j]
            vari[r+1] = omega + pterm + qterm      
        return vari

    """
        Description:
            optimize GARCH parameter
        Input:
            formatedData: the DataFrame with processed(compute variance and mean) and organized bar data
    """
    def optimize_parameters_batch(self, residuals):
        paramSize = self.p+self.q+1
        theta0 = [0.05 for x in range(paramSize)]
        fig = plt.figure()
        ax1 = fig.add_subplot(111)
        for symbol in residuals.keys():
            try:
                xopt = optimize.fmin(func=self.maximum_likelihood, x0=theta0, args=(residuals[symbol].values,),
                                     xtol=0.0001, disp=False)
                VaR = np.multiply(np.sqrt(self.garch(xopt, residuals[symbol].values)), stats.norm.ppf(0.95))
                ax1.clear()
                ax1.plot(range(len(VaR)), VaR, 'r--')
                ax1.plot(range(len(VaR)), -VaR, 'r--')
                ax1.plot(range(len(residuals[symbol].values)), residuals[symbol].values, 'b-')
                ax1.set_ylim([-0.25, 0.25])
                plt.pause(0.01)
                if len(xopt) == paramSize:
                    self.omega = np.append(self.omega, xopt[0])
                    self.alpha = np.append(self.alpha, [xopt[1:-self.q]], axis=0)
                    self.beta = np.append(self.beta, [xopt[self.p+1:]], axis=0)
            except Exception as e:
                traceback.print_exc()
                print(str(e))

    def optimize_parameters_benchmark(self, benchmark="snp500", risk_level=0.9, centered=True):
        benchmark_series = self.preprocess.retrieve_benchmark(benchmark=benchmark).mean(axis=1)
        #benchmark_series = pd.read_csv("/home/meng/Downloads/SP500.csv")["Close"]
        benchmark_change = benchmark_series.pct_change(periods=1).fillna(0)
        mu = benchmark_change.mean()
        residual = benchmark_change.subtract(mu)
        paramSize = self.p + self.q + 1
        theta0 = [0.05 for x in range(paramSize)]
        try:
            xopt = optimize.fmin(func=self.maximum_likelihood, x0=theta0, args=(residual.values,),
                                    xtol=0.0001, disp=False)
            VaR = np.multiply(np.sqrt(self.garch(xopt, residual.values)), stats.norm.ppf(risk_level))
            fig = plt.figure()
            ax1 = fig.add_subplot(111)
            if centered:
                # plot centered VaR boundary
                ax1.plot(range(len(VaR)), VaR, 'r--')
                ax1.plot(range(len(VaR)), -VaR, 'r--')
                ax1.plot(range(len(residual.values)), residual.values, 'b-')
            else:
                # plot actual VaR boundary
                ax1.plot(range(len(VaR)), benchmark_series.shift(periods=1).values+np.multiply(benchmark_series.values, VaR), 'r--')
                ax1.plot(range(len(VaR)), benchmark_series.shift(periods=1).values-np.multiply(benchmark_series.values, VaR), 'r--')
                ax1.plot(range(len(benchmark_series.values)), benchmark_series.values, 'b-')
            plt.show()
        except Exception as e:
            traceback.print_exc()
            print(str(e))


if __name__ == "__main__":
    garch = GARCH(1, 1, 180)
    #data = garch.prep_data()
    #garch.optimize_parameters_batch(data)
    #plt.plot(garch.alpha[:, 0], garch.beta[:, 0], 'b.', label="1st order")
    #plt.show()
    garch.optimize_parameters_benchmark(benchmark="snp500", risk_level=0.9, centered=False)

