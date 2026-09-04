import axios from 'axios'

import type {HealthResponse} from '@/types/health'
import request from '@/utils/request'

const isHealthResponse = (value: unknown): value is HealthResponse => {
    return typeof value === 'object'
        && value !== null
        && 'status' in value
        && typeof value.status === 'string'
}

export const getHealth = async (): Promise<HealthResponse> => {
    try {
        const response = await request.get<HealthResponse>('/api/v1/health')
        return response.data
    } catch (error: unknown) {
        if (axios.isAxiosError(error) && isHealthResponse(error.response?.data)) {
            return error.response.data
        }
        throw error
    }
}
