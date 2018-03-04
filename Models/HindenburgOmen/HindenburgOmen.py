#!/usr/bin/python3

import datetime
import numpy as np
import pandas as pd
from Preprocess import Preprocess
import matplotlib.pyplot as plt
from sklearn.neighbors import KernelDensity


class HindenburgOmen:

    def __init__(self):
        self.preprocess = Preprocess(lag=80)

    def benchmarkCriteria(self):
        benchmark = self.preprocess.retrieve_benchmark("snp500")
        latest = benchmark.iloc[-1]["open"]
        prev50 = benchmark.iloc[-50]["open"]
        return latest > prev50

    def computePriceHighLow(self):
        daily_price = self.preprocess.retrieve_open_close()
        recent_average = daily_price.iloc[-1].xs("average", level="field", axis=0).transpose().to_frame(name="recent")
        high_low = self.preprocess.retrieve_high_low()
        return recent_average, high_low

    def computeRatio(self, recent_average, high_low):
        symbols = pd.concat([recent_average, high_low], axis=1, join='inner')  # type: pd.DataFrame
        ratios = pd.DataFrame(index=symbols.index)  # type: pd.DataFrame
        for reference in [13, 26, 52]:
            high, low, column = "", "", ""
            if reference == 13:
                low, high, column = "low13", "high13", "ratio13"
            elif reference == 26:
                low, high, column = "low26", "high26", "ratio26"
            elif reference == 52:
                low, high, column = "low52", "high52", "ratio52"
            columns = [low, high]
            processed = pd.concat([high_low[columns], recent_average], axis=1, join='inner')  # type: pd.DataFrame
            ratios[column] = np.clip(np.divide(np.subtract(processed["recent"], processed[low]),
                                               np.subtract(processed[high], processed[low])),
                                     0, 1)
            ratios[column] = ratios[column].fillna(ratios[column].mean())  # fill missing values
        return ratios

    def highLowerCriteria(self, highlow52ratio=0.028):
        # highlow52ratio: The daily number of new 52-week highs and new 52-week lows are both greater than a threshold
        r, hl = self.computePriceHighLow()
        ratios = rh.computeRatio(r, hl)
        total = len(ratios.index)
        high52ratio = len(ratios[ratios["ratio52"] > 0.999].index) / total
        low52ratio =  len(ratios[ratios["ratio52"] < 0.001].index) / total
        print("52-week-high:", high52ratio, "52-week-low:", low52ratio)
        return high52ratio > highlow52ratio and low52ratio > highlow52ratio


if __name__ == "__main__":
    # Hindenburg Omen Criteria
    highlow52ratio = 0.028  # The daily number of new 52-week highs and new 52-week lows are both greater than a threshold

    rh = HindenburgOmen()
    c1 = rh.highLowerCriteria()
    c2 = rh.benchmarkCriteria()



