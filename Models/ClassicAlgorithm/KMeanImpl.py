#!/usr/bin/python

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
import matplotlib.pyplot as plt
from Preprocess import Preprocess
from PCAImpl import PCAImpl

class KMeanImpl:
    
    def __init__(self, lag=30, density=0.8, init='k-means++', n_clusters=4, max_iter = 300, algorithm='auto', tol=1e-4, verbose=False):
        self.cluster_weight = []
        self.n_clusters = n_clusters
        self.kmean = KMeans(init=init, n_clusters=n_clusters, max_iter = max_iter, algorithm=algorithm, tol=tol, verbose=verbose)
        self.preprocess = Preprocess(data='fundamental_ratios', lag=lag, density=density)
        
    
    def getData(self, dimReduction = None, lagged = True):
        scaled_data = self.preprocess.getData('scaled', lagged)
        if dimReduction is None:
            return scaled_data
        if dimReduction == 'PCA':
            PCA = PCAImpl()
            PCA.fit(scaled_data)
            f = scaled_data.as_matrix()
            c = np.transpose(np.array(PCA.getComponents(0.05)))
            pc = np.matmul(f, c)
            cols = range(PCA.numPC)
            reducedData = pd.DataFrame(data=pc, index=scaled_data.index, columns=cols)
            print reducedData
            return reducedData
        
        
    def train(self, dimReduction = None):
        df = self.getData(dimReduction, True)
        df['label'] = self.kmean.fit_predict(df)
        return df
        
        
    def predict(self, dimReduction = None):
        df = self.getData(dimReduction, False)
        df['label'] = self.kmean.predict(df)
        return df
    
    
    def visualizeCluster(self, data):
        dim = len(data.columns)-1
        pltIndex = 1
        print data
        for colx in data.columns:
            if colx == 'label':
                continue
            for coly in data.columns:
                if coly == 'label':
                    continue
                plt.subplot(dim, dim, pltIndex)
                pltIndex += 1
                if colx >= coly:
                    continue
                for label in range(self.n_clusters):
                    x = (data[data['label']==label])[colx]
                    y = (data[data['label']==label])[coly]
                    plt.xlabel(colx)
                    plt.ylabel(coly)
                    plt.scatter(x, y)
        plt.show()

"""
kmc = KMeanImpl()
#data = kmc.getData()
data2 = kmc.fit_predict('PCA')
#print data2[data2['label'] == 3]
#print data.shape
kmc.visualizeCluster(data2)
"""
#k = KMeans(init='random', n_clusters=50, max_iter = 300, algorithm='full', tol=1e-25, verbose=True)
#p = k.fit_predict(data.as_matrix())
#for i in p:
#    print i
