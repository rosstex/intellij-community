/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.formatting

import com.intellij.formatting.engine.State
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.FormatRangesInfo
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.util.containers.Stack

interface BlockProcessor {
  fun processLeafBlock(block: Block)
  fun processCompositeBlock(block: Block)
}

class VcsAwareFormatRangesInfo(val formattingRanges: List<TextRange>,
                               private val insertedRanges: List<TextRange>): FormatRangesInfo() 
{
  constructor(changedTextRange: TextRange): this(listOf(changedTextRange), emptyList())

  override fun getRangesToFormat() = formattingRanges

  override fun isOnInsertedLine(offset: Int) = insertedRanges.find { it.contains(offset) } != null
  
}

    
class AdditionalRangesExtractor(private val formatRanges: FormatTextRanges) : BlockProcessor {
  val extraRanges = mutableListOf<ExtraReformatRanges>()

  override fun processLeafBlock(block: Block) = Unit

  override fun processCompositeBlock(block: Block) {
    if (block is AbstractBlock) {
      block.getExtraRangesToFormat(formatRanges)?.let { extraRanges.add(it) }
    }
  }
}

class ExtraReformatRanges(val ranges: List<TextRange> = emptyList()) {
  constructor(range: TextRange): this(listOf(range))
}

fun FormatTextRanges.mergeWith(extraRanges: ExtraReformatRanges) {
  extraRanges.ranges.forEach { add(it, false) }
}


class AdjustFormatRangesState(var currentRoot: Block,
                              val formatRanges: FormatTextRanges) : State() {
  
  private val extractor = AdditionalRangesExtractor(formatRanges)
  private val state = Stack(currentRoot)
  
  init {
    setOnDone({
      extractor.extraRanges.forEach { formatRanges.mergeWith(it) }
    })
  }
  
  override fun doIteration() {
    val currentBlock = state.pop()
    processBlock(currentBlock)
    isDone = state.isEmpty()
  }

  private fun processBlock(currentBlock: Block) {
    if (formatRanges.isReadOnly(currentBlock.textRange, false)) {
      extractor.processLeafBlock(currentBlock)
    }
    else {
      val children = currentBlock.subBlocks
      if (children.isEmpty()) {
        extractor.processLeafBlock(currentBlock)
        return
      }

      extractor.processCompositeBlock(currentBlock)

      children
          .filterNot { formatRanges.isReadOnly(it.textRange, false) }
          .reversed()
          .forEach { state.push(it) }
    }
  }

}