"""Phase 6 - torch models: an MLP over summary features and a 1D-CNN over the
joint-angle sequences. Both are small (CPU-friendly) and configurable so the
same definitions are reused by tuning (Phase 8) and ablation (Phase 9).
"""
from __future__ import annotations

import torch
import torch.nn as nn


class MLP(nn.Module):
    def __init__(self, in_dim, n_classes, hidden_sizes=(128, 64), dropout=0.3):
        super().__init__()
        layers = []
        prev = in_dim
        for h in hidden_sizes:
            layers += [nn.Linear(prev, h), nn.ReLU(), nn.Dropout(dropout)]
            prev = h
        layers.append(nn.Linear(prev, n_classes))
        self.net = nn.Sequential(*layers)

    def forward(self, x):
        return self.net(x)


class CNN1D(nn.Module):
    """1D-CNN over (batch, channels=66, time). Global-average-pools then classifies."""

    def __init__(self, in_channels, n_classes, channels=(32, 64), kernel_size=5,
                 dropout=0.3):
        super().__init__()
        blocks = []
        prev = in_channels
        for c in channels:
            blocks += [
                nn.Conv1d(prev, c, kernel_size, padding=kernel_size // 2),
                nn.BatchNorm1d(c),
                nn.ReLU(),
                nn.MaxPool1d(2),
            ]
            prev = c
        self.features = nn.Sequential(*blocks)
        self.dropout = nn.Dropout(dropout)
        self.head = nn.Linear(prev, n_classes)

    def forward(self, x):
        # x: (batch, time, channels) -> (batch, channels, time)
        x = x.transpose(1, 2)
        x = self.features(x)
        x = x.mean(dim=2)          # global average pool over time
        x = self.dropout(x)
        return self.head(x)


def count_params(model):
    return sum(p.numel() for p in model.parameters())
