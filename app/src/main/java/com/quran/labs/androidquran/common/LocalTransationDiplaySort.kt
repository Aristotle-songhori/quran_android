package com.quran.labs.androidquran.common

class LocalTransationDiplaySort : Comparator<LocalTranslation> {
  override fun compare(p0: LocalTranslation, p1: LocalTranslation): Int {
    return p0.displayOrder.compareTo(p1.displayOrder);
  }
}
