import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'

/**
 * Bases and equipment types are needed by almost every page (filters, pickers,
 * validation hints). They are small reference tables so they are cached for the
 * lifetime of the tab and refreshed on demand.
 */
export function useReferenceData() {
  const [bases, setBases] = useState([])
  const [equipmentTypes, setEquipmentTypes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const [baseList, typeList] = await Promise.all([api.bases(), api.equipmentTypes()])
      setBases(baseList)
      setEquipmentTypes(typeList)
      setError(null)
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  const equipmentById = Object.fromEntries(equipmentTypes.map((type) => [type.id, type]))
  const baseById = Object.fromEntries(bases.map((base) => [base.id, base]))

  return { bases, equipmentTypes, equipmentById, baseById, loading, error, reload }
}

/**
 * Personnel is only readable by ADMIN and BASE_COMMANDER, so it is kept out of
 * useReferenceData: a 403 for a logistics officer must not break the base and
 * equipment pickers used by that role. An empty list simply means the caller
 * has no access to the roster.
 */
export function usePersonnel(baseId) {
  const [personnel, setPersonnel] = useState([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setPersonnel(await api.personnel(baseId ? { baseId } : undefined))
    } catch {
      setPersonnel([])
    } finally {
      setLoading(false)
    }
  }, [baseId])

  useEffect(() => {
    load()
  }, [load])

  return { personnel, loading, reload: load }
}

export default useReferenceData
