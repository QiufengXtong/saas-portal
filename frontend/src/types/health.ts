export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN' | string

export interface HealthComponent {
    status: HealthStatus
    components?: Record<string, HealthComponent>
}

export interface HealthResponse extends HealthComponent {
    components?: Record<string, HealthComponent>
}
