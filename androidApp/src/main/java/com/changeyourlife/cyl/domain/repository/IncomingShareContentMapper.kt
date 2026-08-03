package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.IncomingShareItem
import com.changeyourlife.cyl.domain.model.PageBlock

interface IncomingShareContentMapper {
    suspend fun map(items: List<IncomingShareItem>): List<PageBlock>
}
