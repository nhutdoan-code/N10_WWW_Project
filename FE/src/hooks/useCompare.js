import { useContext } from 'react';
import { CompareContext } from '../context/CompareContext';

export default function useCompare() {
  return useContext(CompareContext);
}
